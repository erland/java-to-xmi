package info.isaksson.erland.javatoxmi;

import info.isaksson.erland.javatoxmi.core.JavaToXmiOptions;
import info.isaksson.erland.javatoxmi.core.JavaToXmiResult;
import info.isaksson.erland.javatoxmi.core.JavaToXmiService;
import info.isaksson.erland.javatoxmi.uml.AssociationPolicy;
import info.isaksson.erland.javatoxmi.uml.NestedTypesMode;
import info.isaksson.erland.javatoxmi.report.ReportGenerator;
import info.isaksson.erland.javatoxmi.ir.IrJson;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.bridge.JModelToIrAdapter;

import org.eclipse.uml2.uml.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 1–3 scaffold: CLI entrypoint + basic argument validation + deterministic source scanning.
 *
 * Later steps will add:
 * - Java AST extraction
 * - UML model building
 * - XMI serialization
 */
public final class Main {

    private static final JavaToXmiService SERVICE = new JavaToXmiService();

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Testable entrypoint that returns an exit code instead of calling System.exit.
     */
    public static int run(String[] args) {
        CliArgs parsed;
        try {
            parsed = CliArgs.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            CliArgs.printHelp();
            return 1;
}

        if (parsed.help) {
            CliArgs.printHelp();
            return 0;
        }

        if (parsed.ir == null && parsed.source == null) {
            System.err.println("Error: --source is required.");
            System.err.println();
            CliArgs.printHelp();
            return 1;
}

final Path sourcePath;
if (parsed.ir != null && !parsed.ir.isBlank()) {
    sourcePath = null;
} else {
    sourcePath = Paths.get(parsed.source).toAbsolutePath().normalize();
    if (!Files.exists(sourcePath)) {
        System.err.println("Error: --source does not exist: " + sourcePath);
        return 1;
    }
    if (!Files.isDirectory(sourcePath)) {
        System.err.println("Error: --source must be a directory: " + sourcePath);
        return 1;
    }
}


        // Resolve output paths
        final String modelName = (parsed.name != null && !parsed.name.isBlank())
                ? parsed.name
                : (sourcePath != null ? sourcePath.getFileName().toString() : "model");

        final Path xmiOut = resolveXmiOutput(parsed.output, sourcePath);
        final Path reportOut = resolveReportOutput(parsed.report, xmiOut);

        try {
            Files.createDirectories(xmiOut.toAbsolutePath().normalize().getParent());
            Files.createDirectories(reportOut.toAbsolutePath().normalize().getParent());
        } catch (IOException e) {
            System.err.println("Error: could not create output directory.");
            System.err.println(e.getMessage());
            return 2;
}

// IR-first mode: read IR JSON and emit XMI directly (for Node/TS/React/Angular extractors)
if (parsed.ir != null && !parsed.ir.isBlank()) {
    final Path irPath = Paths.get(parsed.ir).toAbsolutePath().normalize();
    if (!Files.exists(irPath) || Files.isDirectory(irPath)) {
        System.err.println("Error: --ir must point to an existing IR JSON file: " + irPath);
        return 1;
    }

    final IrModel irModel;
    try {
        irModel = IrJson.read(irPath);
    } catch (IOException e) {
        System.err.println("Error: could not read IR JSON: " + irPath);
        System.err.println(e.getMessage());
        return 2;
    }

    final String irModelName = (parsed.name != null && !parsed.name.isBlank())
            ? parsed.name
            : stripExtension(irPath.getFileName().toString());

    try {
        JavaToXmiOptions opts = toCoreOptions(parsed, irModelName);
        JavaToXmiResult res = SERVICE.generateFromIr(irModel, opts);
        Files.writeString(xmiOut, res.xmiString);
    } catch (RuntimeException | IOException ex) {
        System.err.println("Error: XMI emission from IR failed.");
        System.err.println(ex.getMessage());
        return 2;
    }

    // Optional minimal report (only if user explicitly set --report)
    if (parsed.report != null && !parsed.report.isBlank()) {
        try {
            writeIrModeReport(reportOut, irPath, xmiOut, irModel);
        } catch (IOException e) {
            System.err.println("Error: could not write report to: " + reportOut);
            System.err.println(e.getMessage());
            return 2;
        }
    }

    System.out.println(
            "java-to-xmi (IR mode)\n" +
            "- IR: " + irPath + "\n" +
            "- XMI: " + xmiOut + "\n" +
            (parsed.report != null ? "- Report: " + reportOut + "\n" : "") +
            "- Classifiers: " + (irModel.classifiers == null ? 0 : irModel.classifiers.size()) + "\n" +
            "- Relations: " + (irModel.relations == null ? 0 : irModel.relations.size())
    );
    return 0;
}

        // Core pipeline (scan + extract + UML build + XMI string)
        final JavaToXmiResult res;
        try {
            JavaToXmiOptions opts = toCoreOptions(parsed, modelName);
            opts.includeTests = parsed.includeTests;
            res = SERVICE.generateFromSource(sourcePath, parsed.excludes, opts);
            Files.writeString(xmiOut, res.xmiString);
        } catch (RuntimeException | IOException e) {
            System.err.println("Error: conversion failed.");
            System.err.println(e.getMessage());
            return 2;
        }

        final List<Path> javaFiles = res.javaFiles;

        // Optional: export cross-language IR snapshot
        if (parsed.writeIr != null && !parsed.writeIr.isBlank()) {
            final Path irOut = resolveIrOutput(parsed.writeIr, xmiOut);
            try {
                Files.createDirectories(irOut.toAbsolutePath().normalize().getParent());
                IrModel outIr = new JModelToIrAdapter().toIr(res.jModel);
                IrJson.write(outIr, irOut);
            } catch (IOException e) {
                System.err.println("Error: could not write IR to: " + irOut);
                System.err.println(e.getMessage());
                return 2;
            }
        }

        // Step 6: report generation
        try {
            ReportGenerator.writeMarkdown(
                    reportOut,
                    sourcePath,
                    xmiOut,
                    res.jModel,
                    res.umlModel,
                    res.stats,
                    javaFiles,
                    parsed.includeTests,
                    parsed.excludes,
                    parsed.failOnUnresolved
            );
        } catch (IOException e) {
            System.err.println("Error: could not write report to: " + reportOut);
            System.err.println(e.getMessage());
            return 2;
}

        // Exit code rules
        if (parsed.failOnUnresolved && res.unresolvedTypeCount > 0) {
            System.err.println("Unresolved (unknown) types present (" + res.unresolvedTypeCount + ") and --fail-on-unresolved is set.");
            System.err.println("See report: " + reportOut);
            return 3;
}

        System.out.println(
                "java-to-xmi\n" +
                "- Source: " + sourcePath + "\n" +
                "- XMI: " + xmiOut + "\n" +
                "- Report: " + reportOut + "\n" +
                "- Java files: " + javaFiles.size() + "\n" +
                "- Types: " + res.jModel.types.size() + "\n" +
                "- Parse errors: " + res.jModel.parseErrors.size() + "\n" +
                "- External refs (stubbed): " + res.jModel.externalTypeRefs.size() + "\n" +
                "- Unresolved (unknown): " + res.unresolvedTypeCount
        );
        return 0;
    }

    private static JavaToXmiOptions toCoreOptions(CliArgs parsed, String modelName) {
        JavaToXmiOptions o = new JavaToXmiOptions();
        o.modelName = modelName;
        o.includeStereotypes = !parsed.noStereotypes;
        o.includeDependencies = parsed.deps;
        o.associationPolicy = parsed.associationPolicy;
        o.nestedTypesMode = parsed.nestedTypesMode;
        o.includeAccessors = parsed.includeAccessors;
        o.includeConstructors = parsed.includeConstructors;
        o.failOnUnresolved = parsed.failOnUnresolved;
        return o;
    }

    private static Path resolveIrOutput(String irArg, Path xmiOut) {
        if (irArg != null && irArg.toLowerCase().endsWith(".json")) {
            return Paths.get(irArg).toAbsolutePath().normalize();
        }
        String dir = (irArg == null || irArg.isBlank()) ? xmiOut.getParent().toString() : irArg;
        return Paths.get(dir).toAbsolutePath().normalize().resolve("model.ir.json");
    }

    private static Path resolveXmiOutput(String outputArg, Path sourcePath) {
        // If user supplies a path ending with .xmi, treat it as the XMI file path.
        // Otherwise treat it as an output directory and write model.xmi inside it.
        if (outputArg != null && outputArg.toLowerCase().endsWith(".xmi")) {
            return Paths.get(outputArg).toAbsolutePath().normalize();
        }
        String dir = (outputArg == null || outputArg.isBlank()) ? "./output" : outputArg;
        return Paths.get(dir).toAbsolutePath().normalize().resolve("model.xmi");
    }

    private static Path resolveReportOutput(String reportArg, Path xmiOut) {
        if (reportArg != null && !reportArg.isBlank()) {
            return Paths.get(reportArg).toAbsolutePath().normalize();
        }
        return xmiOut.getParent().resolve("report.md");
    }

    /** Minimal CLI argument parsing without external dependencies. */
    static final class CliArgs {
        boolean help = false;
        String source;
        String output = "./output";

        // Step 6 flags
        String name;
        String report;

        // IR mode
        String ir;
        String writeIr;
        boolean failOnUnresolved = false;

        // Step 9: backwards compatibility
        boolean noStereotypes = false;

        // Association emission policy
        AssociationPolicy associationPolicy = AssociationPolicy.RESOLVED;

        // Nested types exposure mode
        NestedTypesMode nestedTypesMode = NestedTypesMode.UML;

        // Dependencies (method signatures + conservative call graph).
        // Defaults to true (use --deps false to disable).
        boolean deps = true;

        // Operations (methods/constructors) emission controls.
        // Defaults to false to keep diagrams cleaner.
        boolean includeAccessors = false;
        boolean includeConstructors = false;

        // Step 2 flags
        boolean includeTests = false;
        final List<String> excludes = new ArrayList<>();

        static CliArgs parse(String[] args) {
            CliArgs out = new CliArgs();

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a == null) continue;

                // support --exclude=glob
                if (a.startsWith("--exclude=")) {
                    out.excludes.add(a.substring("--exclude=".length()));
                    continue;
                }

                switch (a) {
                    case "--help":
                    case "-h":
                        out.help = true;
                        break;
                    case "--source":
                        out.source = requireValue(args, ++i, "--source");
                        break;
                    case "--output":
                        out.output = requireValue(args, ++i, "--output");
                        break;
                    case "--name":
                        out.name = requireValue(args, ++i, "--name");
                        break;
                    case "--report":
                        out.report = requireValue(args, ++i, "--report");
                        break;
                    case "--ir":
                        out.ir = requireValue(args, ++i, "--ir");
                        break;
                    case "--write-ir":
                        out.writeIr = requireValue(args, ++i, "--write-ir");
                        break;
                    case "--fail-on-unresolved":
                        out.failOnUnresolved = parseBoolean(requireValue(args, ++i, "--fail-on-unresolved"), "--fail-on-unresolved");
                        break;
                    case "--exclude":
                        out.excludes.add(requireValue(args, ++i, "--exclude"));
                        break;
                    case "--include-tests":
                        out.includeTests = true;
                        break;
                    case "--no-stereotypes":
                        out.noStereotypes = true;
                        break;
                    case "--associations":
                        out.associationPolicy = AssociationPolicy.parseCli(requireValue(args, ++i, "--associations"));
                        break;
                    case "--nested-types":
                        out.nestedTypesMode = NestedTypesMode.parseCli(requireValue(args, ++i, "--nested-types"));
                        break;
                    case "--deps":
                        out.deps = parseBoolean(requireValue(args, ++i, "--deps"), "--deps");
                        break;
                    case "--include-accessors":
                        out.includeAccessors = parseBoolean(requireValue(args, ++i, "--include-accessors"), "--include-accessors");
                        break;
                    case "--include-constructors":
                        out.includeConstructors = parseBoolean(requireValue(args, ++i, "--include-constructors"), "--include-constructors");
                        break;
                    default:
                        if (a.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown argument: " + a);
                        }
                        // allow a bare path as shorthand for --source
                        if (out.source == null) {
                            out.source = a;
                        } else {
                            throw new IllegalArgumentException("Unexpected extra argument: " + a);
                        }
                }
            }

            return out;
        }

        static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            String v = args[index];
            if (v == null || v.isBlank() || v.startsWith("--")) {
                throw new IllegalArgumentException("Invalid value for " + flag + ": " + v);
            }
            return v;
        }

        static boolean parseBoolean(String v, String flag) {
            if (v == null) throw new IllegalArgumentException("Missing value for " + flag);
            String s = v.trim().toLowerCase();
            if (s.equals("true") || s.equals("1") || s.equals("yes")) return true;
            if (s.equals("false") || s.equals("0") || s.equals("no")) return false;
            throw new IllegalArgumentException("Invalid boolean for " + flag + ": " + v);
        }

        static boolean looksLikeBoolean(String v) {
            if (v == null) return false;
            String s = v.trim().toLowerCase();
            return s.equals("true") || s.equals("false") || s.equals("1") || s.equals("0") || s.equals("yes") || s.equals("no");
        }

        static void printHelp() {
            System.out.println(
                    "java-to-xmi\n" +
                    "\n" +
                    "Usage:\n" +
                    "  java -jar java-to-xmi.jar --source <path> [--output <dir|file.xmi>] [options]\n" +
                    "\n" +
                    "Options:\n" +
                    "  --source <path>        Root folder containing Java sources (required)\n" +
                    "  --output <path>        Output folder (default: ./output)\n" +
                    "  --exclude <glob>       Exclude paths matching glob (repeatable). Matches are evaluated\n" +
                    "                         against paths *relative to --source* using '/' separators.\n" +
                    "                         Also supports --exclude=<glob>.\n" +
                    "  --include-tests        Include common test folders (default: excluded)\n" +
                    "  --no-stereotypes       Backwards-compatibility: skip building the JavaAnnotations\n" +
                    "                         profile and do not emit stereotype applications.\n" +
                    "  --associations <mode>  Control association emission from fields. Modes:\n" +
                    "                         none | jpa | resolved | smart (default: resolved)\n" +
                    "  --nested-types <mode>  Control nested member type exposure. Modes:\n" +
                    "                         uml | uml+import | flatten (default: uml)\n" +
                    "  --deps <bool>          Emit dependency relationships (method signatures + conservative call graph).\n" +
                    "                         Default: true. When enabled, dependencies that duplicate existing\n" +
                    "                         associations are suppressed, and multiple findings between the same\n" +
                    "                         two classifiers are merged into a single dependency.\n" +
                    "  --include-accessors <bool>  Include getter/setter operations when a corresponding field exists.\n" +
                    "                         Default: false (getters/setters are suppressed when a field exists).\n" +
                    "  --include-constructors <bool> Include constructors as operations.\n" +
                    "                         Default: false.\n" +
                    "  -h, --help             Show help\n" +
                    "\n" +
                    "Examples:\n" +
                    "  java -jar target/java-to-xmi.jar --source samples/mini --output out\n" +
                    "  java -jar target/java-to-xmi.jar samples/mini\n" +
                    "  java -jar target/java-to-xmi.jar --source . --exclude \"**/generated/**\" --exclude \"**/*Test.java\"\n"
            );
        }
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) return fileName;
        return fileName.substring(0, idx);
    }

    /**
     * Minimal report for IR-first mode (no Java extractor inputs available).
     */
    private static void writeIrModeReport(Path reportOut, Path irPath, Path xmiOut, IrModel irModel) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# java-to-xmi report (IR mode)\n\n");
        sb.append("- IR: ").append(irPath.toAbsolutePath().normalize()).append("\n");
        sb.append("- XMI: ").append(xmiOut.toAbsolutePath().normalize()).append("\n");
        sb.append("- IR schemaVersion: ").append(irModel == null ? "?" : irModel.schemaVersion).append("\n");
        int cls = irModel == null || irModel.classifiers == null ? 0 : irModel.classifiers.size();
        int rel = irModel == null || irModel.relations == null ? 0 : irModel.relations.size();
        sb.append("- Classifiers: ").append(cls).append("\n");
        sb.append("- Relations: ").append(rel).append("\n");
        sb.append("\n");
        Files.writeString(reportOut, sb.toString());
    }
}

package info.isaksson.erland.javatoxmi;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.UnresolvedTypeRef;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;
import info.isaksson.erland.javatoxmi.uml.UmlBuildStats;
import info.isaksson.erland.javatoxmi.uml.AssociationPolicy;
import info.isaksson.erland.javatoxmi.uml.NestedTypesMode;
import info.isaksson.erland.javatoxmi.xmi.XmiWriter;
import info.isaksson.erland.javatoxmi.report.ReportGenerator;

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

        if (parsed.source == null) {
            System.err.println("Error: --source is required.");
            System.err.println();
            CliArgs.printHelp();
            return 1;
}

        final Path sourcePath = Paths.get(parsed.source).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            System.err.println("Error: --source does not exist: " + sourcePath);
            return 1;
}
        if (!Files.isDirectory(sourcePath)) {
            System.err.println("Error: --source must be a directory: " + sourcePath);
            return 1;
}

        // Resolve output paths
        final String modelName = (parsed.name != null && !parsed.name.isBlank())
                ? parsed.name
                : sourcePath.getFileName().toString();

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

        // Step 2: deterministic source scanning
        final List<Path> javaFiles;
        try {
            javaFiles = SourceScanner.scan(sourcePath, parsed.excludes, parsed.includeTests);
        } catch (IOException e) {
            System.err.println("Error: could not scan source directory: " + sourcePath);
            System.err.println(e.getMessage());
            return 2;
}

        // Step 3: parse + extract Java semantic model
        final JModel jModel;
        try {
            jModel = new JavaExtractor().extract(sourcePath, javaFiles, parsed.deps);
        } catch (RuntimeException ex) {
            System.err.println("Error: extraction failed.");
            System.err.println(ex.getMessage());
            return 2;
}

        // Step 4: build UML object graph
        final UmlBuilder.Result umlResult;
        try {
            umlResult = new UmlBuilder().build(
                    jModel,
                    modelName,
                    !parsed.noStereotypes,
                    parsed.associationPolicy,
                    parsed.nestedTypesMode,
                    parsed.deps,
                    parsed.includeAccessors,
                    parsed.includeConstructors
            );
        } catch (RuntimeException ex) {
            System.err.println("Error: UML build failed.");
            System.err.println(ex.getMessage());
            return 2;
}

        // Step 5: XMI export
        try {
            if (parsed.noStereotypes) {
                XmiWriter.write(umlResult.umlModel, xmiOut);
            } else {
                XmiWriter.write(umlResult.umlModel, jModel, xmiOut);
            }
        } catch (IOException e) {
            System.err.println("Error: could not write XMI to: " + xmiOut);
            System.err.println(e.getMessage());
            return 2;
}

        // Step 6: report generation
        try {
            ReportGenerator.writeMarkdown(
                    reportOut,
                    sourcePath,
                    xmiOut,
                    jModel,
                    umlResult.umlModel,
                    umlResult.stats,
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
        if (parsed.failOnUnresolved && !jModel.unresolvedTypes.isEmpty()) {
            System.err.println("Unresolved (unknown) types present (" + jModel.unresolvedTypes.size() + ") and --fail-on-unresolved is set.");
            System.err.println("See report: " + reportOut);
            return 3;
}

        System.out.println(
                "java-to-xmi\n" +
                "- Source: " + sourcePath + "\n" +
                "- XMI: " + xmiOut + "\n" +
                "- Report: " + reportOut + "\n" +
                "- Java files: " + javaFiles.size() + "\n" +
                "- Types: " + jModel.types.size() + "\n" +
                "- Parse errors: " + jModel.parseErrors.size() + "\n" +
                "- External refs (stubbed): " + jModel.externalTypeRefs.size() + "\n" +
                "- Unresolved (unknown): " + jModel.unresolvedTypes.size()
        );
        return 0;
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
        boolean failOnUnresolved = false;

        // Step 9: backwards compatibility
        boolean noStereotypes = false;

        // Association emission policy
        AssociationPolicy associationPolicy = AssociationPolicy.RESOLVED;

        // Nested types exposure mode
        NestedTypesMode nestedTypesMode = NestedTypesMode.UML;

        // Dependencies (method signatures + conservative call graph).
        // Defaults to false to keep diagrams clean (associations already capture most structural links).
        boolean deps = false;

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
                    "                         Default: false. When enabled, dependencies that duplicate existing\n" +
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
}

package se.erland.javatoxmi;

import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.UnresolvedTypeRef;
import se.erland.javatoxmi.uml.UmlBuilder;
import se.erland.javatoxmi.uml.UmlBuildStats;
import se.erland.javatoxmi.xmi.XmiWriter;
import se.erland.javatoxmi.report.ReportGenerator;

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
        CliArgs parsed;
        try {
            parsed = CliArgs.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            CliArgs.printHelp();
            System.exit(1); // invalid arguments
            return;
        }

        if (parsed.help) {
            CliArgs.printHelp();
            return;
        }

        if (parsed.source == null) {
            System.err.println("Error: --source is required.");
            System.err.println();
            CliArgs.printHelp();
            System.exit(1);
            return;
        }

        final Path sourcePath = Paths.get(parsed.source).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            System.err.println("Error: --source does not exist: " + sourcePath);
            System.exit(1);
            return;
        }
        if (!Files.isDirectory(sourcePath)) {
            System.err.println("Error: --source must be a directory: " + sourcePath);
            System.exit(1);
            return;
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
            System.exit(2);
            return;
        }

        // Step 2: deterministic source scanning
        final List<Path> javaFiles;
        try {
            javaFiles = SourceScanner.scan(sourcePath, parsed.excludes, parsed.includeTests);
        } catch (IOException e) {
            System.err.println("Error: could not scan source directory: " + sourcePath);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        // Step 3: parse + extract Java semantic model
        final JModel jModel;
        try {
            jModel = new JavaExtractor().extract(sourcePath, javaFiles);
        } catch (RuntimeException ex) {
            System.err.println("Error: extraction failed.");
            System.err.println(ex.getMessage());
            System.exit(2);
            return;
        }

        // Step 4: build UML object graph
        final UmlBuilder.Result umlResult;
        try {
            umlResult = new UmlBuilder().build(jModel, modelName);
        } catch (RuntimeException ex) {
            System.err.println("Error: UML build failed.");
            System.err.println(ex.getMessage());
            System.exit(2);
            return;
        }

        // Step 5: XMI export
        try {
            XmiWriter.write(umlResult.umlModel, xmiOut);
        } catch (IOException e) {
            System.err.println("Error: could not write XMI to: " + xmiOut);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
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
            System.exit(2);
            return;
        }

        // Exit code rules
        if (parsed.failOnUnresolved && !jModel.unresolvedTypes.isEmpty()) {
            System.err.println("Unresolved types present (" + jModel.unresolvedTypes.size() + ") and --fail-on-unresolved is set.");
            System.err.println("See report: " + reportOut);
            System.exit(3);
            return;
        }System.out.println("java-to-xmi\n" +
                "- Source: " + sourcePath + "\n" +
                "- XMI: " + xmiOut + "\n" +
                "- Report: " + reportOut + "\n" +
                "- Java files: " + javaFiles.size() + "\n" +
                "- Types: " + jModel.types.size() + "\n" +
                "- Unresolved: " + jModel.unresolvedTypes.size());
System.exit(0);
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

    /** Minimal CLI argument parsing without external d
 without external dependencies (Step 1/2). */
    static final class CliArgs {
        boolean help = false;
        String source;
        String output = "./output";

        // Step 6 flags
        String name;
        String report;
        boolean failOnUnresolved = false;

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

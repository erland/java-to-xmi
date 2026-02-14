package se.erland.javatoxmi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Step 1 scaffold: CLI entrypoint + basic argument validation.
 *
 * Later steps will add:
 * - source scanning
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
            System.exit(2);
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
            System.exit(2);
            return;
        }

        final Path sourcePath = Paths.get(parsed.source).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            System.err.println("Error: --source does not exist: " + sourcePath);
            System.exit(2);
            return;
        }
        if (!Files.isDirectory(sourcePath)) {
            System.err.println("Error: --source must be a directory: " + sourcePath);
            System.exit(2);
            return;
        }

        final Path outputPath = Paths.get(parsed.output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            System.err.println("Error: could not create output directory: " + outputPath);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        // Step 1: only scaffold. Produce placeholder outputs so the CLI feels tangible.
        final Path xmiOut = outputPath.resolve("model.xmi");
        final Path reportOut = outputPath.resolve("report.md");
        try {
            if (!Files.exists(xmiOut)) {
                Files.writeString(xmiOut, "<!-- Placeholder. Step 5 will generate real UML XMI here. -->\n");
            }
            if (!Files.exists(reportOut)) {
                Files.writeString(reportOut,
                        "# java-to-xmi report\n\n" +
                        "This is a scaffold run (Step 1).\n\n" +
                        "- Source: `" + sourcePath + "`\n" +
                        "- Output: `" + outputPath + "`\n\n" +
                        "Next steps will scan Java files, build a UML model, and export deterministic XMI.\n");
            }
        } catch (IOException e) {
            System.err.println("Error: could not write placeholder outputs to: " + outputPath);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("java-to-xmi (scaffold)\n" +
                "- Source: " + sourcePath + "\n" +
                "- Output: " + outputPath + "\n" +
                "\n" +
                "Wrote placeholder files:\n" +
                "- " + xmiOut + "\n" +
                "- " + reportOut);
    }

    /** Minimal CLI argument parsing without external dependencies (Step 1). */
    static final class CliArgs {
        boolean help = false;
        String source;
        String output = "./output";

        static CliArgs parse(String[] args) {
            CliArgs out = new CliArgs();

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a == null) continue;

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

        static void printHelp() {
            System.out.println(
                    "java-to-xmi\n" +
                    "\n" +
                    "Usage:\n" +
                    "  java -jar java-to-xmi.jar --source <path> [--output <path>]\n" +
                    "\n" +
                    "Options:\n" +
                    "  --source <path>   Root folder containing Java sources (required)\n" +
                    "  --output <path>   Output folder (default: ./output)\n" +
                    "  -h, --help        Show help\n" +
                    "\n" +
                    "Examples:\n" +
                    "  java -jar target/java-to-xmi.jar --source samples/mini --output out\n" +
                    "  java -jar target/java-to-xmi.jar samples/mini\n"
            );
        }
    }
}

package se.erland.javatoxmi;

import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.UnresolvedTypeRef;

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

        // Step 2: deterministic source scanning (still no AST/UML yet).
        final List<Path> javaFiles;
        try {
            javaFiles = SourceScanner.scan(sourcePath, parsed.excludes, parsed.includeTests);
        } catch (IOException e) {
            System.err.println("Error: could not scan source directory: " + sourcePath);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        // Still scaffold: Produce placeholder outputs so the CLI feels tangible.

        // Step 3: parse + extract a compact Java semantic model (baseline type resolution).
        final JModel jModel = new JavaExtractor().extract(sourcePath, javaFiles);

        final Path xmiOut = outputPath.resolve("model.xmi");
        final Path reportOut = outputPath.resolve("report.md");
        try {
            if (!Files.exists(xmiOut)) {
                Files.writeString(xmiOut, "<!-- Placeholder. Step 5 will generate real UML XMI here. -->\n");
            }

            // Always write report (it's quick and useful even in scaffold mode)
            StringBuilder report = new StringBuilder();
            report.append("# java-to-xmi report\n\n");
            report.append("This is a scaffold run (Steps 1–3).\n\n");
            report.append("- Source: `").append(sourcePath).append("`\n");
            report.append("- Output: `").append(outputPath).append("`\n");
            report.append("- Java files discovered: **").append(javaFiles.size()).append("**\n");
            report.append("- Include tests: **").append(parsed.includeTests).append("**\n");
            report.append("- Excludes: ").append(parsed.excludes.isEmpty() ? "_(none)_" : "`" + String.join("`, `", parsed.excludes) + "`").append("\n\n");

            report.append("## Discovered files\n");
            for (Path p : javaFiles) {
                report.append("- `").append(sourcePath.relativize(p).toString().replace("\\", "/")).append("`\n");
            }

            report.append("\n## Extracted types\n");
            report.append("Types extracted: **").append(jModel.types.size()).append("**\n\n");
            for (JType t : jModel.types) {
                report.append("- `").append(t.qualifiedName).append("` (").append(t.kind).append(")");
                if (t.extendsType != null && !t.extendsType.isBlank()) {
                    report.append(" extends `").append(t.extendsType).append("`");
                }
                if (!t.implementsTypes.isEmpty()) {
                    report.append(" implements ");
                    report.append(t.implementsTypes.stream().map(x -> "`" + x + "`").collect(java.util.stream.Collectors.joining(", ")));
                }
                report.append("\n");
            }

            report.append("\n## Parse errors\n");
            if (jModel.parseErrors.isEmpty()) {
                report.append("_(none)_\n");
            } else {
                for (String pe : jModel.parseErrors) report.append("- ").append(pe).append("\n");
            }

            report.append("\n## Unresolved type references\n");
            if (jModel.unresolvedTypes.isEmpty()) {
                report.append("_(none)_\n");
            } else {
                // stable ordering
                java.util.List<UnresolvedTypeRef> ur = new java.util.ArrayList<>(jModel.unresolvedTypes);
                ur.sort(java.util.Comparator.comparing(u -> u.referencedType + "|" + u.fromQualifiedType + "|" + u.where));
                for (UnresolvedTypeRef u : ur) report.append("- ").append(u.toString()).append("\n");
            }
            report.append("\n");
            report.append("Next steps will build a UML model and export deterministic XMI.\n");

            Files.writeString(reportOut, report.toString());
        } catch (IOException e) {
            System.err.println("Error: could not write outputs to: " + outputPath);
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("java-to-xmi (scaffold, Steps 1–3)\n" +
                "- Source: " + sourcePath + "\n" +
                "- Output: " + outputPath + "\n" +
                "- Java files: " + javaFiles.size() + "\n" +
                "\n" +
                "Wrote files:\n" +
                "- " + xmiOut + "\n" +
                "- " + reportOut);
    }

    /** Minimal CLI argument parsing without external dependencies (Step 1/2). */
    static final class CliArgs {
        boolean help = false;
        String source;
        String output = "./output";

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

        static void printHelp() {
            System.out.println(
                    "java-to-xmi\n" +
                    "\n" +
                    "Usage:\n" +
                    "  java -jar java-to-xmi.jar --source <path> [--output <path>] [options]\n" +
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

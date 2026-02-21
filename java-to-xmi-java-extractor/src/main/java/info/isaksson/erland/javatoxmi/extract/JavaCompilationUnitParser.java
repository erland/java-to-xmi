package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source files into {@link CompilationUnit}s, collecting parse errors into the {@link JModel}.
 */
final class JavaCompilationUnitParser {

    private JavaCompilationUnitParser() {}

    static List<ParsedUnit> parseAll(JavaParser parser, Path sourceRoot, List<Path> javaFiles, JModel model) {
        List<ParsedUnit> units = new ArrayList<>();
        for (Path f : javaFiles) {
            try {
                String code = Files.readString(f, StandardCharsets.UTF_8);
                CompilationUnit cu = parser.parse(code).getResult()
                        .orElseThrow(() -> new ParseProblemException(List.of()));
                units.add(new ParsedUnit(f, cu));
            } catch (ParseProblemException e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": parse error (" + e.getProblems().size() + " problems)");
            } catch (IOException e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": IO error (" + e.getMessage() + ")");
            } catch (Exception e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": error (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
        }
        return units;
    }

    private static String rel(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toString().replace('\\', '/');
        }
    }
}

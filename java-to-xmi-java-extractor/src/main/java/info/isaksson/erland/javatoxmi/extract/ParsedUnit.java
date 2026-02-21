package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;

/** Package-private parsed compilation unit wrapper. */
final class ParsedUnit {
    final Path file;
    final CompilationUnit cu;

    ParsedUnit(Path file, CompilationUnit cu) {
        this.file = file;
        this.cu = cu;
    }
}

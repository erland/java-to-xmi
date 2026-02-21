package info.isaksson.erland.javatoxmi;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test helper to resolve paths relative to the repository root in multi-module builds. */
final class TestRepoPaths {

    private TestRepoPaths() {}

    static Path resolveRepoRoot() {
        Path p = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10; i++) {
            if (Files.isDirectory(p.resolve("samples"))) return p;
            p = p.getParent();
            if (p == null) break;
        }
        throw new IllegalStateException("Could not locate repo root (folder containing 'samples'). Start dir: " + Path.of("").toAbsolutePath());
    }

    static Path resolveSamplesMini() {
        return resolveRepoRoot().resolve("samples").resolve("mini");
    }
}

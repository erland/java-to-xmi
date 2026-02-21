package info.isaksson.erland.javatoxmi.testutil;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test helper for resolving paths when running from Maven submodules. */
public final class TestPaths {
    private TestPaths() {}

    /** Find repo root by walking upwards until a 'samples' directory exists. */
    public static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && p != null; i++) {
            if (Files.isDirectory(p.resolve("samples"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("Could not locate repo root (no 'samples' directory found in parents).");
    }

    /** Resolve a repository-relative path (e.g. 'samples/mini/src/main/java'). */
    public static Path resolveInRepo(String repoRelative) {
        if (repoRelative == null || repoRelative.isBlank()) {
            throw new IllegalArgumentException("repoRelative must not be blank");
        }
        return repoRoot().resolve(repoRelative).toAbsolutePath().normalize();
    }
}

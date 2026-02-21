package info.isaksson.erland.javatoxmi.io;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Step 2: Deterministic Java source discovery with exclude/include rules.
 *
 * The scanner returns a stable, sorted list of .java files under a source root.
 */
public final class SourceScanner {

    private SourceScanner() {}

    /**
     * Scan for .java files under {@code sourceRoot}.
     *
     * @param sourceRoot root folder to scan
     * @param excludeGlobs list of glob patterns (matched against the path relative to sourceRoot, using '/' separators)
     * @param includeTests whether to include common test folders (e.g. src/test, test)
     */
    public static List<Path> scan(Path sourceRoot, List<String> excludeGlobs, boolean includeTests) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");

        final List<Predicate<Path>> excludeMatchers = compileExcludeMatchers(excludeGlobs);

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            List<Path> out = new ArrayList<>();
            stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> includeTests || !looksLikeTestPath(sourceRoot, p))
                .filter(p -> !isInCommonBuildDir(sourceRoot, p))
                .filter(p -> !matchesAny(sourceRoot, p, excludeMatchers))
                .forEach(out::add);

            // Stable deterministic ordering (relative path)
            out.sort(Comparator.comparing(p -> normalizeRel(sourceRoot, p)));
            return out;
        }
    }

    private static boolean matchesAny(Path root, Path absolutePath, List<Predicate<Path>> matchers) {
        if (matchers.isEmpty()) return false;
        final Path rel = root.relativize(absolutePath);
        for (Predicate<Path> m : matchers) {
            if (m.test(rel)) return true;
        }
        return false;
    }

    private static List<Predicate<Path>> compileExcludeMatchers(List<String> excludeGlobs) {
        if (excludeGlobs == null || excludeGlobs.isEmpty()) return Collections.emptyList();

        FileSystem fs = FileSystems.getDefault();
        List<Predicate<Path>> out = new ArrayList<>();
        for (String raw : excludeGlobs) {
            if (raw == null) continue;
            String pattern = raw.trim();
            if (pattern.isEmpty()) continue;

            // Normalize to use forward slashes to be consistent across OSes.
            pattern = pattern.replace("\\", "/");

            // If the user supplies a directory pattern without wildcards, treat it as "under this directory".
            if (!pattern.contains("*") && !pattern.contains("?") && !pattern.contains("[") && !pattern.endsWith("/")) {
                pattern = pattern + "/**";
            }

            final var matcher = fs.getPathMatcher("glob:" + pattern);
            out.add(p -> matcher.matches(Path.of(normalizePathString(p))));
        }
        return out;
    }

    private static boolean looksLikeTestPath(Path root, Path absolutePath) {
        String rel = normalizeRel(root, absolutePath);
        // Common Maven/Gradle test roots, plus generic /test/ folders.
        return rel.startsWith("src/test/")
                || rel.startsWith("src/integrationTest/")
                || rel.startsWith("src/it/")
                || rel.startsWith("test/")
                || rel.contains("/test/")
                || rel.contains("/tests/");
    }

    private static boolean isInCommonBuildDir(Path root, Path absolutePath) {
        String rel = normalizeRel(root, absolutePath);
        // Avoid scanning under build output folders by default.
        return rel.startsWith("target/")
                || rel.startsWith("build/")
                || rel.startsWith("out/")
                || rel.startsWith(".git/")
                || rel.startsWith(".idea/")
                || rel.startsWith(".gradle/")
                || rel.startsWith("node_modules/");
    }

    private static String normalizeRel(Path root, Path p) {
        return normalizePathString(root.relativize(p));
    }

    private static String normalizePathString(Path p) {
        return p.toString().replace("\\", "/");
    }
}

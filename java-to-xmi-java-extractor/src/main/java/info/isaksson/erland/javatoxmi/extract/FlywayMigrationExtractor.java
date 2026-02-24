package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import info.isaksson.erland.javatoxmi.model.JMigrationArtifact;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract Flyway-style database migration artifacts.
 *
 * <p>Two sources are supported:</p>
 * <ul>
 *   <li>SQL/resource migrations under {@code db/migration/} (and below) following Flyway naming conventions</li>
 *   <li>Java-based migrations (best-effort) where a class extends {@code BaseJavaMigration} or implements {@code JavaMigration}</li>
 * </ul>
 */
public final class FlywayMigrationExtractor {

    // Version part allows digits/letters/underscore/dot (Flyway supports e.g. V2_1__... and V2.1__...)
    private static final Pattern VERSIONED = Pattern.compile(
            "^V([0-9][0-9A-Za-z_.]*)__(.+)\\.(sql|conf|csv|tsv|yml|yaml|json)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE = Pattern.compile("^R__(.+)\\.(sql|conf|csv|tsv|yml|yaml|json)$", Pattern.CASE_INSENSITIVE);

    public void extract(JModel model, List<ParsedUnit> units) {
        if (model == null || model.sourceRoot == null) return;

        extractSqlMigrations(model);
        extractJavaMigrations(model, units);

        // Deterministic ordering
        model.migrationArtifacts.sort(Comparator
                .comparing((JMigrationArtifact a) -> a.kind.name())
                .thenComparing(a -> a.version == null ? "" : a.version)
                .thenComparing(a -> a.description == null ? "" : a.description)
                .thenComparing(a -> a.path));
    }

    private void extractSqlMigrations(JModel model) {
        Path root = model.sourceRoot;
        List<JMigrationArtifact> found = new ArrayList<>();
        try {
            if (!Files.exists(root)) return;
            try (var stream = Files.walk(root)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(p -> isUnderDbMigration(p, root))
                        .forEach(p -> {
                            String fileName = p.getFileName().toString();
                            JMigrationArtifact art = parseFlywayFilename(root, p, fileName);
                            if (art != null) found.add(art);
                        });
            }
        } catch (IOException ignored) {
            // Best-effort: ignore IO issues.
        }
        model.migrationArtifacts.addAll(found);
    }

    private void extractJavaMigrations(JModel model, List<ParsedUnit> units) {
        if (units == null) return;

        for (ParsedUnit u : units) {
            CompilationUnit cu = u == null ? null : u.cu;
            if (cu == null) continue;
            for (TypeDeclaration<?> td : cu.getTypes()) {
                if (!(td instanceof ClassOrInterfaceDeclaration c)) continue;
                if (c.isInterface()) continue;

                boolean looksLikeFlyway = c.getExtendedTypes().stream().anyMatch(t -> simpleName(t.getNameAsString()).equals("BaseJavaMigration"))
                        || c.getImplementedTypes().stream().anyMatch(t -> simpleName(t.getNameAsString()).equals("JavaMigration"));
                if (!looksLikeFlyway) continue;

                String qn = tryQualifiedName(cu, c);
                if (qn == null || qn.isBlank()) continue;

                String id = "flyway:java:" + qn;
                JMigrationArtifact art = new JMigrationArtifact(id, null, c.getNameAsString(), qn, "java", JMigrationArtifact.Kind.JAVA);
                model.migrationArtifacts.add(art);
            }
        }
    }

    private static boolean isUnderDbMigration(Path file, Path root) {
        // match any path segment "db/migration" (common Flyway convention)
        Path rel;
        try {
            rel = root.relativize(file);
        } catch (Exception e) {
            rel = file;
        }
        String norm = rel.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return norm.contains("/db/migration/");
    }

    private static JMigrationArtifact parseFlywayFilename(Path root, Path file, String fileName) {
        Matcher m = VERSIONED.matcher(fileName);
        if (m.matches()) {
            String version = normalizeVersion(m.group(1));
            String desc = normalizeDescription(m.group(2));
            String rel = safeRel(root, file);
            String id = "flyway:V:" + version + ":" + rel;
            JMigrationArtifact art = new JMigrationArtifact(id, version, desc, rel, "sql", JMigrationArtifact.Kind.VERSIONED);
            return art;
        }
        m = REPEATABLE.matcher(fileName);
        if (m.matches()) {
            String desc = normalizeDescription(m.group(1));
            String rel = safeRel(root, file);
            String id = "flyway:R:" + desc + ":" + rel;
            return new JMigrationArtifact(id, null, desc, rel, "sql", JMigrationArtifact.Kind.REPEATABLE);
        }
        return null;
    }

    private static String safeRel(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toString().replace('\\', '/');
        }
    }

    private static String normalizeDescription(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('_', ' ');
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) return null;
        return raw.trim().replace('_', '.');
    }

    private static String simpleName(String n) {
        if (n == null) return "";
        int idx = n.lastIndexOf('.');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    private static String tryQualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration c) {
        if (c == null) return null;
        String pkg = cu == null || cu.getPackageDeclaration().isEmpty()
                ? ""
                : cu.getPackageDeclaration().get().getNameAsString();
        String name = c.getNameAsString();
        if (name == null || name.isBlank()) return null;
        return pkg.isBlank() ? name : pkg + "." + name;
    }
}

package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.ir.IrRuntime;
import info.isaksson.erland.javatoxmi.model.JMigrationArtifact;

import org.eclipse.uml2.uml.Artifact;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.UMLPackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Emits DB migration artifacts (e.g. Flyway) into the UML model. */
public final class UmlMigrationArtifactEmitter {

    // Tag keys (kept as runtime.* so they are normalized/sorted with other runtime tags)
    public static final String TAG_MIGRATION_VERSION = "runtime.migration.version";
    public static final String TAG_MIGRATION_DESCRIPTION = "runtime.migration.description";
    public static final String TAG_MIGRATION_TYPE = "runtime.migration.type";
    public static final String TAG_MIGRATION_PATH = "runtime.migration.path";
    public static final String TAG_MIGRATION_KIND = "runtime.migration.kind";

    public void emit(UmlBuildContext ctx, List<JMigrationArtifact> artifacts) {
        if (ctx == null || artifacts == null || artifacts.isEmpty()) return;

        List<JMigrationArtifact> sorted = new ArrayList<>(artifacts);
        sorted.sort(Comparator
                .comparing((JMigrationArtifact a) -> a == null ? "" : (a.kind == null ? "" : a.kind.name()))
                .thenComparing(a -> a == null ? "" : (a.version == null ? "" : a.version))
                .thenComparing(a -> a == null ? "" : (a.description == null ? "" : a.description))
                .thenComparing(a -> a == null ? "" : (a.path == null ? "" : a.path)));

        Package pkg = ctx.model.getNestedPackage("DatabaseMigrations");
        if (pkg == null) pkg = ctx.model.createNestedPackage("DatabaseMigrations");

        Map<String, Integer> nameCounts = new LinkedHashMap<>();

        for (JMigrationArtifact a : sorted) {
            if (a == null) continue;

            String baseName = toArtifactName(a);
            int n = nameCounts.getOrDefault(baseName, 0);
            nameCounts.put(baseName, n + 1);
            String name = n == 0 ? baseName : baseName + " (" + (n + 1) + ")";

            Artifact umlArt = (Artifact) pkg.createPackagedElement(name, UMLPackage.Literals.ARTIFACT);

            // stable id
            String id = a.id == null ? ("migration:" + name) : a.id;
            UmlBuilderSupport.annotateId(umlArt, id);

            // Mark runtime stereotype (post-processed into stereotype application during XMI write)
            UmlBuilderSupport.annotateRuntimeStereotype(umlArt, IrRuntime.ST_FLYWAY_MIGRATION);

            // Add tags
            Map<String, String> tags = new LinkedHashMap<>();
            if (a.version != null && !a.version.isBlank()) tags.put(TAG_MIGRATION_VERSION, a.version);
            if (a.description != null && !a.description.isBlank()) tags.put(TAG_MIGRATION_DESCRIPTION, a.description);
            if (a.type != null && !a.type.isBlank()) tags.put(TAG_MIGRATION_TYPE, a.type);
            if (a.path != null && !a.path.isBlank()) tags.put(TAG_MIGRATION_PATH, a.path);
            if (a.kind != null) tags.put(TAG_MIGRATION_KIND, a.kind.name());
            if (a.tags != null && !a.tags.isEmpty()) {
                for (var e : a.tags.entrySet()) {
                    if (e.getKey() == null || e.getKey().isBlank()) continue;
                    tags.put(e.getKey(), e.getValue());
                }
            }
            UmlBuilderSupport.annotateTags(umlArt, tags);

            // Optional: link from model to the artifact (helps navigation).
            Dependency dep = ctx.model.createDependency(umlArt);
            UmlBuilderSupport.annotateId(dep, "migration-dep:" + id);
        }
    }

    private static String toArtifactName(JMigrationArtifact a) {
        if (a.kind == JMigrationArtifact.Kind.REPEATABLE) {
            String d = a.description == null ? "" : a.description;
            return "R " + d;
        }
        if (a.kind == JMigrationArtifact.Kind.JAVA) {
            return "Java " + (a.description == null ? "Migration" : a.description);
        }
        String v = a.version == null ? "" : a.version;
        String d = a.description == null ? "" : a.description;
        if (d.isBlank()) return "V" + v;
        return "V" + v + " " + d;
    }
}

package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JMigrationArtifact;
import org.eclipse.uml2.uml.Artifact;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlMigrationArtifactEmitterTest {

    @Test
    void createsArtifactsWithRuntimeMarkerAndTags() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("M");

        UmlBuildStats stats = new UmlBuildStats();
        UmlBuildContext ctx = new UmlBuildContext(
                model,
                stats,
                new MultiplicityResolver(),
                AssociationPolicy.RESOLVED,
                NestedTypesMode.UML,
                false,
                true,
                true
        );

        JMigrationArtifact a = new JMigrationArtifact(
                "flyway:V:1:src/main/resources/db/migration/V1__init.sql",
                "1",
                "init",
                "src/main/resources/db/migration/V1__init.sql",
                "sql",
                JMigrationArtifact.Kind.VERSIONED
        );

        new UmlMigrationArtifactEmitter().emit(ctx, List.of(a));

        var pkg = model.getNestedPackage("DatabaseMigrations");
        assertNotNull(pkg);

        Artifact art = pkg.getOwnedType("V1 init") instanceof Artifact ar ? ar : null;
        assertNotNull(art, "Expected artifact named 'V1 init'");

        var runtime = art.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE);
        assertNotNull(runtime);
        assertEquals("FlywayMigration", runtime.getDetails().get(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_KEY));

        var tags = art.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE);
        assertNotNull(tags);
        assertEquals("1", tags.getDetails().get(UmlMigrationArtifactEmitter.TAG_MIGRATION_VERSION));
        assertEquals("init", tags.getDetails().get(UmlMigrationArtifactEmitter.TAG_MIGRATION_DESCRIPTION));
    }
}

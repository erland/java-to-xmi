package se.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.AssociationPolicy;
import se.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterCompositionAndTagsSmokeTest {

    @Test
    void compositionAndRelationTagsSurviveToXmi() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jm = new JavaExtractor().extract(source, files);

        var uml = new UmlBuilder().build(jm, "java-to-xmi", true, AssociationPolicy.JPA_ONLY).umlModel;

        Path out = Files.createTempFile("java-to-xmi", ".xmi");
        XmiWriter.write(uml, jm, out);

        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        // Profile + stereotype apps should be present.
        assertTrue(xmi.contains("<profileApplication"), "Expected a UML profileApplication");
        assertTrue(xmi.contains(":J2XTags"), "Expected tool tag stereotype applications");

        // OrphanRemoval-driven composition should appear in XMI.
        assertTrue(xmi.contains("orphanRemoval=\"true\"") || xmi.contains("jpaOrphanRemoval=\"true\""),
                "Expected orphanRemoval true to be preserved (annotation or tool tag)");

        // Aggregation can appear both as UML property aggregation and as a tool tag.
        assertTrue(xmi.contains("aggregation=\"composite\""), "Expected composite aggregation in XMI");

        // Relation source tagging should be present for at least one element.
        assertTrue(xmi.contains("relationSource=\"jpa\""), "Expected relationSource=jpa tool tag");
    }
}

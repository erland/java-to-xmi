package se.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;
import se.erland.javatoxmi.uml.UmlIdStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterStereotypeInjectionTest {

    @Test
    void injectsJavaAnnotationStereotypeApplicationsAsXmiExtension() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-xmi-ann");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        // Define a tiny annotation + use it with values.
        Files.writeString(src.resolve("Entity.java"),
                "package com.acme;\n" +
                        "public @interface Entity { String value() default \"\"; }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("Person.java"),
                "package com.acme;\n" +
                        "@Entity(\"person\")\n" +
                        "public class Person { }\n",
                StandardCharsets.UTF_8);

        List<Path> files = List.of(src.resolve("Entity.java"), src.resolve("Person.java"));
        JModel jm = new JavaExtractor().extract(root, files);
        var uml = new UmlBuilder().build(jm, "java-to-xmi").umlModel;

        Path out = Files.createTempFile("java-to-xmi", ".xmi");
        XmiWriter.write(uml, jm, out);

        String xmi = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(xmi.contains("<xmi:Extension extender=\"java-to-xmi\">"), "Expected an injected XMI extension");
        assertTrue(xmi.contains("<j2x:stereotypeApplications>"), "Expected stereotypeApplications section");

        String baseId = "_" + UmlIdStrategy.id("Classifier:com.acme.Person");
        assertTrue(xmi.contains("base=\"" + baseId + "\""), "Expected base classifier id to be referenced");

        // Stereotype ID is deterministic based on qualifiedName + stereotype name.
        String stId = "_" + UmlIdStrategy.id("Stereotype:com.acme.Entity#Entity");
        assertTrue(xmi.contains("stereotype=\"" + stId + "\""), "Expected stereotype id to be referenced");

        // Tag value should be present.
        assertTrue(xmi.contains("<j2x:tag name=\"value\" value=\"person\"/>"), "Expected annotation value tag");
    }
}

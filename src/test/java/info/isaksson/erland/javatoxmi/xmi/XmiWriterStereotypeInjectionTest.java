package info.isaksson.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;
import info.isaksson.erland.javatoxmi.uml.UmlIdStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterStereotypeInjectionTest {

    @Test
    void injectsJavaAnnotationStereotypeApplicationsAsUml2ProfileAndApps() throws Exception {
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

        // Profile application should be present under the model.
        assertTrue(xmi.contains("<profileApplication"), "Expected a UML profileApplication");
        assertTrue(xmi.contains("<appliedProfile href=\"#"), "Expected appliedProfile reference");

        String baseId = "_" + UmlIdStrategy.id("Classifier:com.acme.Person");

        // Stereotype application should be present in UML2-style form.
        assertTrue(
                xmi.contains(":Entity") && xmi.contains("base_Class=\"" + baseId + "\"") && xmi.contains("value=\"person\""),
                "Expected a UML2-style stereotype application for @Entity with value=person"
        );
    }
}

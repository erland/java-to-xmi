package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorAnnotationsTest {

    @Test
    void extractsTypeLevelAnnotationsWithBestEffortQualificationAndValues() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-anns");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        String code = "package com.acme;\n" +
                "import jakarta.persistence.Entity;\n" +
                "import jakarta.persistence.Table;\n" +
                "@Entity\n" +
                "@Table(name=\"person\")\n" +
                "public class Person { }\n";

        Path file = src.resolve("Person.java");
        Files.writeString(file, code, StandardCharsets.UTF_8);

        JModel model = new JavaExtractor().extract(root, List.of(file));
        JType person = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Person")).findFirst().orElseThrow();

        assertNotNull(person.annotations);
        assertEquals(2, person.annotations.size(), "Expected two annotations on Person");

        JAnnotationUse entity = person.annotations.stream().filter(a -> "Entity".equals(a.simpleName)).findFirst().orElseThrow();
        assertEquals("jakarta.persistence.Entity", entity.qualifiedName, "Expected Entity to be qualified via import");
        assertTrue(entity.values.isEmpty(), "Marker annotation should have no values");

        JAnnotationUse table = person.annotations.stream().filter(a -> "Table".equals(a.simpleName)).findFirst().orElseThrow();
        assertEquals("jakarta.persistence.Table", table.qualifiedName, "Expected Table to be qualified via import");
        assertEquals("person", table.values.get("name"));
    }
}

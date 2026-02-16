package se.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;
import se.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterProfileNsUriTest {

    @Test
    void profileEcoreNsUriIsStableConstant() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-nsuri");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("Entity.java"),
                "package com.acme;\n" +
                        "public @interface Entity { String value() default \"\"; }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("C.java"),
                "package com.acme;\n" +
                        "@Entity(\"c\")\n" +
                        "public class C { }\n",
                StandardCharsets.UTF_8);

        List<Path> files = List.of(src.resolve("Entity.java"), src.resolve("C.java"));
        JModel jm = new JavaExtractor().extract(root, files);
        var uml = new UmlBuilder().build(jm, "java-to-xmi").umlModel;

        Path out = Files.createTempFile("java-to-xmi-nsuri", ".xmi");
        XmiWriter.write(uml, jm, out);
        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        assertTrue(
                xmi.contains("nsURI=\"" + JavaAnnotationProfileBuilder.PROFILE_URI + "\""),
                "Expected stable PROFILE_URI in generated Ecore nsURI"
        );

        // Guard against the previous non-deterministic UUID-ish segment.
        assertFalse(
                xmi.contains("http://java-to-xmi/schemas/JavaAnnotations/_"),
                "nsURI should not include non-deterministic _<id> segments"
        );
    }
}

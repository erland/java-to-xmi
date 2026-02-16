package se.erland.javatoxmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;
import se.erland.javatoxmi.xmi.XmiWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BackwardsCompatibilityNoStereotypesTest {

    @Test
    void noStereotypesOptionSkipsProfileAndXmiExtension() throws Exception {
        Path root = Files.createTempDirectory("j2x-nos-ster");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("Entity.java"),
                "package com.acme; public @interface Entity { String value() default \"\"; }\n",
                StandardCharsets.UTF_8);
        Files.writeString(src.resolve("A.java"),
                "package com.acme; @Entity(\"x\") public class A {}\n",
                StandardCharsets.UTF_8);

        JavaExtractor extractor = new JavaExtractor();
        JModel jm = extractor.extract(root, List.of(src.resolve("Entity.java"), src.resolve("A.java")));

        UmlBuilder.Result res = new UmlBuilder().build(jm, "m", false);
        Path out = Files.createTempFile("nos", ".xmi");
        XmiWriter.write(res.umlModel, out);
        String xml = Files.readString(out, StandardCharsets.UTF_8);

        assertFalse(xml.contains("JavaAnnotations"), "Expected no JavaAnnotations profile in no-stereotypes mode");
        assertFalse(xml.contains("xmi:Extension extender=\"java-to-xmi\""), "Expected no injected XMI extension");
        assertFalse(xml.contains("j2x:"), "Expected no j2x extension content");
    }
}

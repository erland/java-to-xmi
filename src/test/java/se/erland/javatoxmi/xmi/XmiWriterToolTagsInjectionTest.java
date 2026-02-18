package se.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterToolTagsInjectionTest {

    @Test
    void injectsToolTagStereotypeApplicationsForElementsWithTagsAnnotation() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-xmi-tags");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("A.java"),
                "package com.acme;\n" +
                        "import java.util.List;\n" +
                        "import javax.validation.constraints.Size;\n" +
                        "import javax.validation.constraints.NotNull;\n" +
                        "public class A {\n" +
                        "  @NotNull\n" +
                        "  @Size(min=1, max=3)\n" +
                        "  private List<String> names;\n" +
                        "}\n",
                StandardCharsets.UTF_8);

        List<Path> files = List.of(src.resolve("A.java"));
        JModel jm = new JavaExtractor().extract(root, files);
        var uml = new UmlBuilder().build(jm, "java-to-xmi").umlModel;

        Path out = Files.createTempFile("java-to-xmi", ".xmi");
        XmiWriter.write(uml, jm, out);

        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        // Profile application should be present under the model.
        assertTrue(xmi.contains("<profileApplication"), "Expected a UML profileApplication");

        // Tool tag stereotype application should be present and carry expected attributes.
        assertTrue(xmi.contains(":J2XTags"), "Expected a UML2-style stereotype application for tool tags");
        assertTrue(xmi.contains("base_NamedElement=\"_"), "Expected tool tag apps to reference base_NamedElement");

        // From List<String>
        assertTrue(xmi.contains("collectionKind=\"List\""), "Expected collectionKind tag");
        assertTrue(xmi.contains("elementType=\"String\"") || xmi.contains("elementType=\"java.lang.String\""),
                "Expected elementType tag");

        // From @Size(min=1,max=3)
        assertTrue(xmi.contains("validationSizeMin=\"1\""), "Expected validationSizeMin tag");
        assertTrue(xmi.contains("validationSizeMax=\"3\""), "Expected validationSizeMax tag");
    }
}

package se.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorNestedTypeImportResolutionTest {

    @Test
    void resolvesOuterDotInnerWhenOuterIsExplicitlyImported() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-nested-import");
        Path srcAcme = root.resolve("src/main/java/com/acme");
        Path srcOther = root.resolve("src/main/java/com/other");
        Files.createDirectories(srcAcme);
        Files.createDirectories(srcOther);

        String outerCode = "package com.acme;\n" +
                "public class Outer {\n" +
                "  public static class Inner { }\n" +
                "}\n";
        Path outerFile = srcAcme.resolve("Outer.java");
        Files.writeString(outerFile, outerCode, StandardCharsets.UTF_8);

        String useCode = "package com.other;\n" +
                "import com.acme.Outer;\n" +
                "public class Uses {\n" +
                "  private Outer.Inner inner;\n" +
                "}\n";
        Path useFile = srcOther.resolve("Uses.java");
        Files.writeString(useFile, useCode, StandardCharsets.UTF_8);

        JavaExtractor ex = new JavaExtractor();
        JModel model = ex.extract(root, List.of(outerFile, useFile));

        JType uses = model.types.stream().filter(t -> t.qualifiedName.equals("com.other.Uses")).findFirst().orElseThrow();
        assertEquals("com.acme.Outer.Inner",
                uses.fields.stream().filter(f -> f.name.equals("inner")).findFirst().orElseThrow().type);

        assertTrue(model.unresolvedTypes.stream().noneMatch(u -> u.referencedType.contains("Outer.Inner")),
                "Outer.Inner should resolve via explicit import of Outer");
    }
}
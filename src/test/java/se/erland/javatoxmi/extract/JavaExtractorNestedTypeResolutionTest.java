package se.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorNestedTypeResolutionTest {

    @Test
    void resolvesSimpleNamesToNestedMemberTypesInEnclosingScope() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-nested-resolve");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        String code = "package com.acme;\n" +
                "import java.util.*;\n" +
                "public class Outer {\n" +
                "  public static class Inner {\n" +
                "    private Sibling sib;\n" +
                "  }\n" +
                "  public static class Sibling { }\n" +
                "  private Inner inner;\n" +
                "  private Outer.Inner inner2;\n" +
                "}\n";

        Path file = src.resolve("Outer.java");
        Files.writeString(file, code, StandardCharsets.UTF_8);

        JavaExtractor ex = new JavaExtractor();
        JModel model = ex.extract(root, List.of(file));

        JType outer = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Outer")).findFirst().orElseThrow();
        JType inner = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Outer.Inner")).findFirst().orElseThrow();

        // Outer field type 'Inner' should resolve to nested member type
        assertEquals("com.acme.Outer.Inner", outer.fields.stream().filter(f -> f.name.equals("inner")).findFirst().orElseThrow().type);
        // Outer qualified reference 'Outer.Inner' should also resolve to the project nested type
        assertEquals("com.acme.Outer.Inner", outer.fields.stream().filter(f -> f.name.equals("inner2")).findFirst().orElseThrow().type);

        // Inside Inner, simple name 'Sibling' should resolve using enclosing scope (Outer)
        assertEquals("com.acme.Outer.Sibling", inner.fields.stream().filter(f -> f.name.equals("sib")).findFirst().orElseThrow().type);

        // No unresolved nested names expected
        assertTrue(model.unresolvedTypes.stream().noneMatch(u -> u.referencedType.equals("Inner") || u.referencedType.equals("Sibling")),
                "Nested member type references should not be unresolved");
    }
}

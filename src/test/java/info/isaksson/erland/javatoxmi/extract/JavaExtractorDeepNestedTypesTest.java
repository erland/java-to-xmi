package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorDeepNestedTypesTest {

    @Test
    void extractsDeepNestedMemberTypesWithExplicitOwnershipAndStableNames() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-deep-nested");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        String code = "package com.acme;\n" +
                "public class Outer {\n" +
                "  public static class Inner {\n" +
                "    public class Deep {\n" +
                "      private Sibling s;\n" +
                "    }\n" +
                "  }\n" +
                "  public static class Sibling { }\n" +
                "  private Inner.Deep deep;\n" +
                "}\n";

        Path file = src.resolve("Outer.java");
        Files.writeString(file, code, StandardCharsets.UTF_8);

        JavaExtractor ex = new JavaExtractor();
        JModel model = ex.extract(root, List.of(file));

        JType outer = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Outer")).findFirst().orElseThrow();
        JType inner = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Outer.Inner")).findFirst().orElseThrow();
        JType deep = model.types.stream().filter(t -> t.qualifiedName.equals("com.acme.Outer.Inner.Deep")).findFirst().orElseThrow();

        // Explicit ownership chain
        assertNull(outer.outerQualifiedName);
        assertEquals("com.acme.Outer", inner.outerQualifiedName);
        assertEquals("com.acme.Outer.Inner", deep.outerQualifiedName);

        // IR nesting flags
        assertFalse(outer.isNested);
        assertTrue(inner.isNested);
        assertTrue(deep.isNested);

        // Static-ness semantics: Inner is static, Deep is a non-static inner class
        assertTrue(inner.isStaticNested);
        assertFalse(deep.isStaticNested);

        // Binary names
        assertEquals("com.acme.Outer", outer.binaryName);
        assertEquals("com.acme.Outer$Inner", inner.binaryName);
        assertEquals("com.acme.Outer$Inner$Deep", deep.binaryName);

        // Resolution within deep scope: Sibling should resolve from Outer scope
        assertEquals("com.acme.Outer.Sibling", deep.fields.stream().filter(f -> f.name.equals("s")).findFirst().orElseThrow().type);

        // Resolution from Outer: Inner.Deep should resolve to the project nested type
        assertEquals("com.acme.Outer.Inner.Deep", outer.fields.stream().filter(f -> f.name.equals("deep")).findFirst().orElseThrow().type);

        assertTrue(model.unresolvedTypes.isEmpty(), "No unresolved types expected for nested member references");
    }
}

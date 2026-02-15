package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderEnumLiteralsTest {

    @Test
    void enumLiteralsAreEmittedInDeclarationOrder() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-enum-literals");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        write(src.resolve("ClearanceLevel.java"), """
                package com.acme;
                public enum ClearanceLevel { LOW, MEDIUM, HIGH }
                """);

        var files = SourceScanner.scan(root.resolve("src/main/java"), List.of(), false);
        JModel jModel = new JavaExtractor().extract(root.resolve("src/main/java"), files);

        UmlBuilder.Result result = new UmlBuilder().build(jModel, "Tmp");
        Model model = result.umlModel;

        Classifier c = findClassifierByName(model, "ClearanceLevel");
        assertNotNull(c);
        assertTrue(c instanceof Enumeration);

        Enumeration e = (Enumeration) c;
        List<String> names = e.getOwnedLiterals().stream().map(EnumerationLiteral::getName).collect(Collectors.toList());
        assertEquals(List.of("LOW", "MEDIUM", "HIGH"), names);
    }

    private static void write(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static Classifier findClassifierByName(Package pkg, String name) {
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Classifier) {
                Classifier c = (Classifier) e;
                if (name.equals(c.getName())) return c;
            }
            if (e instanceof Package) {
                Classifier c = findClassifierByName((Package) e, name);
                if (c != null) return c;
            }
        }
        return null;
    }
}

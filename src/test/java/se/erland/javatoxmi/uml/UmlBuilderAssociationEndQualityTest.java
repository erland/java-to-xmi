package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers "association end quality" improvements:
 * - field name becomes role name (Property.name)
 * - end is navigable from the owning class
 * - multiplicity heuristics (Optional, arrays, Map)
 * - the existing owned attribute is reused as the association end (no duplicates)
 */
public class UmlBuilderAssociationEndQualityTest {

    @Test
    void associationEndUsesFieldAttributeNameVisibilityNavigabilityAndMultiplicity() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-assoc-end-quality");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        // Minimal domain: A -> B with multiple field type shapes.
        write(src.resolve("B.java"), """
                package com.acme;
                public class B { }
                """);

        write(src.resolve("A.java"), """
                package com.acme;

                import java.util.Map;
                import java.util.Optional;

                public class A {
                    private B owner;
                    private Optional<B> maybe;
                    private B[] many;
                    private Map<String, B> map;
                }
                """);

        var files = SourceScanner.scan(root.resolve("src/main/java"), List.of(), false);
        JModel jModel = new JavaExtractor().extract(root.resolve("src/main/java"), files);

        UmlBuilder.Result result = new UmlBuilder().build(jModel, "Tmp");
        Model model = result.umlModel;

        Classifier aC = findClassifierByName(model, "A");
        Classifier bC = findClassifierByName(model, "B");
        assertNotNull(aC);
        assertNotNull(bC);
        assertTrue(aC instanceof Class);

        Class a = (Class) aC;

        // For each field, ensure there is exactly one attribute and it is associated.
        assertAssocEnd(a, bC, "owner", 0, 1);
        assertAssocEnd(a, bC, "maybe", 0, 1);
        assertAssocEnd(a, bC, "many", 0, -1);
        assertAssocEnd(a, bC, "map", 0, -1);
    }

    private static void assertAssocEnd(Class a, Classifier b, String fieldName, int lower, int upper) {
        // Exactly one attribute with this name
        long count = a.getOwnedAttributes().stream().filter(p -> fieldName.equals(p.getName())).count();
        assertEquals(1, count, "Expected a single owned attribute named " + fieldName);

        Property attr = a.getOwnedAttributes().stream().filter(p -> fieldName.equals(p.getName())).findFirst().orElseThrow();
        assertNotNull(attr.getAssociation(), "Expected attribute '" + fieldName + "' to be an association end");
        Association assoc = attr.getAssociation();

        // Role name and type
        assertEquals(fieldName, attr.getName(), "Role name should come from field name");
        assertSame(b, attr.getType(), "Field-end type should be the referenced classifier");

        // Multiplicity
        assertEquals(lower, attr.getLower(), "Lower multiplicity mismatch for " + fieldName);
        assertEquals(upper, attr.getUpper(), "Upper multiplicity mismatch for " + fieldName);

        // Navigability: classifier-owned ends are considered navigable by most UML tools.
        // We ensure the field-end stays owned by the class and is referenced from the association memberEnds.
        assertSame(a, attr.getOwner(), "Field-end should remain owned by the class for " + fieldName);
        assertTrue(assoc.getMemberEnds().contains(attr), "Association should reference field-end in memberEnds for " + fieldName);

        for (Property end : assoc.getMemberEnds()) {
            if (end == attr) continue;
            // Keep the opposite end unnamed (or empty) and non-navigable for cleanliness.
            assertTrue(end.getName() == null || end.getName().isBlank(), "Opposite end should be unnamed for " + fieldName);
            // Opposite end should be association-owned.
            assertSame(assoc, end.getOwner(), "Opposite end should be owned by the Association for " + fieldName);
        }
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

    @SuppressWarnings("unused")
    private static List<Association> collectAssociations(Package pkg) {
        List<Association> out = new ArrayList<>();
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Association) out.add((Association) e);
            if (e instanceof Package) out.addAll(collectAssociations((Package) e));
        }
        return out;
    }
}

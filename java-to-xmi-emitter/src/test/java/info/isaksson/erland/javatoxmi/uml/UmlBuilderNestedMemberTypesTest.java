package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderNestedMemberTypesTest {

    @Test
    void nestedMemberTypesAreOwnedByEnclosingClassifier() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-nested-types");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        write(src.resolve("Outer.java"), """
                package com.acme;

                public class Outer {
                  public static class Inner { }
                  public interface IInner { }
                  public enum E { A, B }
                }
                """);

        write(src.resolve("TopI.java"), """
                package com.acme;

                public interface TopI {
                  class Impl { }
                  enum Kind { X, Y }
                }
                """);

        var files = SourceScanner.scan(root.resolve("src/main/java"), List.of(), false);
        JModel jModel = new JavaExtractor().extract(root.resolve("src/main/java"), files);

        UmlBuilder.Result result = new UmlBuilder().build(jModel, "Tmp");
        Model model = result.umlModel;

        Package acme = findPackage(model, "com.acme");
        assertNotNull(acme);

        // Top-level classifiers exist in the package
        org.eclipse.uml2.uml.Type outerT = acme.getOwnedType("Outer");
        assertNotNull(outerT);
        assertTrue(outerT instanceof Classifier);
        Classifier outer = (Classifier) outerT;
        assertTrue(outer instanceof Class);

        org.eclipse.uml2.uml.Type topIT = acme.getOwnedType("TopI");
        assertNotNull(topIT);
        assertTrue(topIT instanceof Classifier);
        Classifier topI = (Classifier) topIT;
        assertTrue(topI instanceof Interface);

        // Nested member types should NOT be top-level owned types
        assertNull(acme.getOwnedType("Inner"));
        assertNull(acme.getOwnedType("IInner"));
        assertNull(acme.getOwnedType("E"));
        assertNull(acme.getOwnedType("Impl"));
        assertNull(acme.getOwnedType("Kind"));

        // Outer nested types
        Classifier inner = findNestedByName(outer, "Inner");
        assertNotNull(inner);
        assertTrue(inner instanceof Class);

        Classifier iinner = findNestedByName(outer, "IInner");
        assertNotNull(iinner);
        assertTrue(iinner instanceof Interface);

        Classifier e = findNestedByName(outer, "E");
        assertNotNull(e);
        assertTrue(e instanceof Enumeration);
        List<String> eLits = ((Enumeration) e).getOwnedLiterals().stream().map(EnumerationLiteral::getName).collect(Collectors.toList());
        assertEquals(List.of("A", "B"), eLits);

        // TopI nested types
        Classifier impl = findNestedByName(topI, "Impl");
        assertNotNull(impl);
        assertTrue(impl instanceof Class);

        Classifier kind = findNestedByName(topI, "Kind");
        assertNotNull(kind);
        assertTrue(kind instanceof Enumeration);
        List<String> kindLits = ((Enumeration) kind).getOwnedLiterals().stream().map(EnumerationLiteral::getName).collect(Collectors.toList());
        assertEquals(List.of("X", "Y"), kindLits);
    }

    @Test
    void nestedMemberTypesCanBeMirroredIntoPackageViaElementImportWhenEnabled() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-nested-types-import");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        write(src.resolve("Outer.java"), """
                package com.acme;

                public class Outer {
                  public static class Inner { }
                  public interface IInner { }
                  public enum E { A, B }
                }
                """);

        var files = SourceScanner.scan(root.resolve("src/main/java"), List.of(), false);
        JModel jModel = new JavaExtractor().extract(root.resolve("src/main/java"), files);

        UmlBuilder.Result result = new UmlBuilder().build(
                jModel,
                "Tmp",
                true,
                AssociationPolicy.RESOLVED,
                NestedTypesMode.UML_IMPORT
        );
        Model model = result.umlModel;

        Package acme = findPackage(model, "com.acme");
        assertNotNull(acme);

        // Still not package-owned
        assertNull(acme.getOwnedType("Inner"));
        assertNull(acme.getOwnedType("IInner"));
        assertNull(acme.getOwnedType("E"));

        // But mirrored via ElementImport for discoverability.
        var imports = acme.getElementImports();
        assertNotNull(imports);
        assertTrue(imports.stream().anyMatch(ei -> ei.getImportedElement() != null && "Inner".equals(ei.getImportedElement().getName())));
        assertTrue(imports.stream().anyMatch(ei -> ei.getImportedElement() != null && "IInner".equals(ei.getImportedElement().getName())));
        assertTrue(imports.stream().anyMatch(ei -> ei.getImportedElement() != null && "E".equals(ei.getImportedElement().getName())));
    }

    private static void write(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static Package findPackage(Package root, String javaPkg) {
        if (javaPkg == null || javaPkg.isBlank()) return root;
        String[] parts = javaPkg.split("\\.");
        Package cur = root;
        for (String p : parts) {
            Package next = cur.getNestedPackage(p);
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

        private static Classifier findNestedByName(org.eclipse.uml2.uml.Namespace owner, String name) {
        // Prefer concrete UML2 API if present (varies by version)
        try {
            java.lang.reflect.Method m = owner.getClass().getMethod("getNestedClassifiers");
            Object v = m.invoke(owner);
            if (v instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof Classifier c && name.equals(c.getName())) {
                        return c;
                    }
                }
            }
        } catch (ReflectiveOperationException ignore) {
            // fall through
        }

        // Fallback: scan owned members (works if UML2 exposes nested types as ownedMembers in this version)
        for (org.eclipse.uml2.uml.NamedElement ne : owner.getOwnedMembers()) {
            if (ne instanceof Classifier c && name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
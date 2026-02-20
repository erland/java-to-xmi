package se.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke test ensuring method-body dependency extraction is emitted as UML Dependencies.
 */
public class UmlBuilderMethodBodyDependenciesSmokeTest {

    @Test
    void emitsUmlDependencyFromMethodBodyCallGraph() throws Exception {
        Path root = Files.createTempDirectory("j2x-deps-smoke");
        Path pkg = root.resolve("p");
        Files.createDirectories(pkg);

        // A references B only from the method body (no fields), so association logic should not interfere.
        Files.writeString(pkg.resolve("A.java"), """
                package p;
                public class A {
                    public void go() {
                        B b = new B();
                        b.ping();
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(pkg.resolve("B.java"), """
                package p;
                public class B {
                    public void ping() {}
                }
                """, StandardCharsets.UTF_8);

        List<Path> files = SourceScanner.scan(root, List.of(), true);
        JModel jModel = new JavaExtractor().extract(root, files, true);
        assertTrue(jModel.parseErrors.isEmpty(), "Expected no parse errors but got: " + jModel.parseErrors);

        UmlBuilder.Result res = new UmlBuilder().build(
                jModel,
                "TestModel",
                false,
                AssociationPolicy.NONE,
                NestedTypesMode.UML,
                true,
                false,
                false
        );
        Model uml = res.umlModel;

        Class a = (Class) uml.getPackagedElements().stream()
                .filter(e -> e instanceof org.eclipse.uml2.uml.Package)
                .flatMap(e -> ((org.eclipse.uml2.uml.Package) e).getPackagedElements().stream())
                .filter(e -> e instanceof Class && "A".equals(((Class) e).getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected class A in UML model"));
        Class b = (Class) uml.getPackagedElements().stream()
                .filter(e -> e instanceof org.eclipse.uml2.uml.Package)
                .flatMap(e -> ((org.eclipse.uml2.uml.Package) e).getPackagedElements().stream())
                .filter(e -> e instanceof Class && "B".equals(((Class) e).getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected class B in UML model"));

        boolean found = false;
        for (var it = uml.eAllContents(); it.hasNext(); ) {
            EObject eo = it.next();
            if (!(eo instanceof Dependency)) continue;
            Dependency d = (Dependency) eo;
            if (d.getClients().contains(a) && d.getSuppliers().contains(b)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected a UML Dependency A -> B derived from method body, but did not find one.");
    }
}

package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.testutil.TestPaths;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal safety net for refactoring association merge/dedup logic.
 */
public class UmlBuilderJpaBidirectionalMergeDedupTest {

    @Test
    void jpaBidirectionalCustomerOrderIsMergedIntoSingleAssociationWithExpectedMultiplicity() throws Exception {
        Path source = TestPaths.resolveInRepo("samples/mini").toAbsolutePath().normalize();
        List<Path> files = SourceScanner.scan(source, List.of(), true);
        JModel jModel = new JavaExtractor().extract(source, files, false);
        assertTrue(jModel.parseErrors.isEmpty(), "Expected no parse errors but got: " + jModel.parseErrors);

        UmlBuilder.Result res = new UmlBuilder().build(
                jModel,
                "Mini",
                false,
                AssociationPolicy.JPA_ONLY,
                NestedTypesMode.UML,
                false,
                false,
                false
        );
        Model uml = res.umlModel;

        Class customer = findClassByQualifiedName(uml, "com.example.jpademo.Customer");
        Class order = findClassByQualifiedName(uml, "com.example.jpademo.Order");

        List<Association> between = associationsBetween(uml, customer, order);
        assertEquals(1, between.size(), "Expected exactly one Association between Customer and Order but got: " + between.size());

        Association a = between.get(0);
        Property endCustomer = a.getMemberEnds().stream().filter(p -> p.getType() == customer).findFirst().orElse(null);
        Property endOrder = a.getMemberEnds().stream().filter(p -> p.getType() == order).findFirst().orElse(null);
        assertNotNull(endCustomer, "Expected an association end typed by Customer");
        assertNotNull(endOrder, "Expected an association end typed by Order");

        // Expect: Order -> Customer is mandatory (1..1) and Customer -> Orders is 1..* (per sample annotations)
        assertEquals(1, endCustomer.getLower(), "Expected Customer end lower=1 but got: " + endCustomer.getLower());
        assertEquals(1, endCustomer.getUpper(), "Expected Customer end upper=1 but got: " + endCustomer.getUpper());

        assertTrue(endOrder.getLower() >= 1, "Expected Order end lower>=1 but got: " + endOrder.getLower());
        assertEquals(-1, endOrder.getUpper(), "Expected Order end upper=-1 (unbounded) but got: " + endOrder.getUpper());
    }

    private static Class findClassByQualifiedName(Model uml, String javaQn) {
        // UML2 qualified names typically use '::' separators, and may be prefixed by the root Model name (e.g. "Mini::...").
        String expectedUmlQn = javaQn.replace(".", "::");

        for (var it = uml.eAllContents(); it.hasNext(); ) {
            EObject eo = it.next();
            if (!(eo instanceof Class)) continue;
            Class c = (Class) eo;

            String qn = c.getQualifiedName();
            if (qn == null) continue;

            String qnDot = qn.replace("::", ".");
            // Exact match (either form)
            if (qn.equals(expectedUmlQn) || qnDot.equals(javaQn)) return c;
            // Model-prefixed match (e.g. "Mini::com::example::jpademo::Customer")
            if (qn.endsWith("::" + expectedUmlQn)) return c;
            if (qnDot.endsWith("." + javaQn)) return c;
        }

        // Fallback: if package structure differs, match by simple name within any package that ends with the expected package.
        String simple = javaQn.substring(javaQn.lastIndexOf('.') + 1);
        String pkg = javaQn.substring(0, javaQn.lastIndexOf('.'));
        for (var it = uml.eAllContents(); it.hasNext(); ) {
            EObject eo = it.next();
            if (!(eo instanceof Class)) continue;
            Class c = (Class) eo;
            if (!simple.equals(c.getName())) continue;
            String qn = c.getQualifiedName();
            if (qn == null) continue;
            String qnDot = qn.replace("::", ".");
            if (qnDot.contains(pkg + ".")) return c;
        }

        throw new AssertionError("Could not find UML Class for " + javaQn);
    }

    private static List<Association> associationsBetween(Model uml, Class a, Class b) {
        List<Association> out = new ArrayList<>();
        for (var it = uml.eAllContents(); it.hasNext(); ) {
            EObject eo = it.next();
            if (!(eo instanceof Association)) continue;
            Association assoc = (Association) eo;
            boolean hasA = assoc.getMemberEnds().stream().anyMatch(p -> p.getType() == a);
            boolean hasB = assoc.getMemberEnds().stream().anyMatch(p -> p.getType() == b);
            if (hasA && hasB) out.add(assoc);
        }
        return out;
    }
}

package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderAssociationPolicyJpaOnlyTest {

    @Test
    void jpaOnly_createsAssociationsForJpaRelationships_only() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        UmlBuilder.Result result = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.JPA_ONLY);
        Model model = result.umlModel;

        Classifier customer = findClassifierByName(model, "Customer");
        Classifier order = findClassifierByName(model, "Order");
        Classifier plainHolder = findClassifierByName(model, "PlainHolder");

        assertNotNull(customer);
        assertNotNull(order);
        assertNotNull(plainHolder);

        // Customer.orders has @OneToMany -> should create association Customer<->Order
        assertTrue(hasAssociationBetween(model, customer, order), "Expected Customer-Order association under JPA_ONLY");

        // PlainHolder.order has no JPA relationship annotations -> should NOT create association
        assertFalse(hasAssociationBetween(model, plainHolder, order), "Did not expect PlainHolder-Order association under JPA_ONLY");
    }

    private static boolean hasAssociationBetween(Package pkg, Classifier a, Classifier b) {
        for (Association assoc : collectAssociations(pkg)) {
            List<Property> ends = assoc.getMemberEnds();
            if (ends.size() != 2) continue;
            var t1 = ends.get(0).getType();
            var t2 = ends.get(1).getType();
            if (t1 == a && t2 == b) return true;
            if (t1 == b && t2 == a) return true;
        }
        return false;
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

    private static List<Association> collectAssociations(Package pkg) {
        List<Association> out = new ArrayList<>();
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Association) out.add((Association) e);
            if (e instanceof Package) out.addAll(collectAssociations((Package) e));
        }
        return out;
    }
}

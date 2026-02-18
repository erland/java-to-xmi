package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Extension;
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

public class UmlBuilderAssociationPolicyNoneTest {

    @Test
    void none_createsNoAssociations_evenForJpaRelationships() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        UmlBuilder.Result result = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.NONE);
        Model model = result.umlModel;

        Classifier customer = findClassifierByName(model, "Customer");
        Classifier order = findClassifierByName(model, "Order");
        assertNotNull(customer);
        assertNotNull(order);

        assertFalse(hasAssociationBetween(model, customer, order), "Did not expect Customer-Order association under NONE");

        // NOTE: UML Profiles introduce metaclass Extensions which are Associations.
        // This test is about domain associations created from Java fields, so we exclude Extensions.
        assertTrue(collectDomainAssociations(model).isEmpty(), "Expected zero domain associations in model under NONE");
    }

    private static boolean hasAssociationBetween(Package pkg, Classifier a, Classifier b) {
        for (Association assoc : collectDomainAssociations(pkg)) {
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

    private static List<Association> collectDomainAssociations(Package pkg) {
        List<Association> out = new ArrayList<>();
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Association && !(e instanceof Extension)) out.add((Association) e);
            if (e instanceof Package) out.addAll(collectDomainAssociations((Package) e));
        }
        return out;
    }
}

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

public class UmlBuilderEmbeddedElementCollectionAttributeOnlyTest {

    @Test
    void embeddedAndElementCollection_doNotCreateAssociationLines() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        // RESOLVED policy would normally create associations for resolved classifier field types.
        Model model = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.RESOLVED).umlModel;

        Classifier customer = findClassifierByName(model, "Customer");
        Classifier address = findClassifierByName(model, "Address");
        assertNotNull(customer);
        assertNotNull(address);

        // Customer.address is @Embedded Address; Customer.previousAddresses is @ElementCollection List<Address>
        // Neither should produce an association line to Address.
        assertFalse(hasAssociationBetween(model, customer, address), "Did not expect Customer-Address association for Embedded/ElementCollection");
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

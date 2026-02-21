package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderEmbeddedAndElementCollectionCompositionTest {

    @Test
    void embeddedAndElementCollection_ofEmbeddable_createCompositionAssociations_butValueLikeDoesNot() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        // RESOLVED policy creates associations for resolved targets.
        Model model = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.RESOLVED).umlModel;

        // Customer.address is @Embedded Address; Customer.previousAddresses is @ElementCollection List<Address>
        Property addressEnd = findAssociationEndByName(model, "address");
        assertNotNull(addressEnd, "Expected an association end named 'address'");
        assertEquals(AggregationKind.COMPOSITE_LITERAL, addressEnd.getAggregation(), "Embedded should be modeled as composition");
        assertOwnerEndIsExactlyOne(addressEnd);

        Property prevEnd = findAssociationEndByName(model, "previousAddresses");
        assertNotNull(prevEnd, "Expected an association end named 'previousAddresses'");
        assertEquals(AggregationKind.COMPOSITE_LITERAL, prevEnd.getAggregation(), "ElementCollection of embeddable should be modeled as composition");
        assertOwnerEndIsExactlyOne(prevEnd);

        // Customer.tags is @ElementCollection List<String> and should remain attribute-only (no association).
        assertNull(findAssociationEndByName(model, "tags"), "Did not expect an association end for element collection of basic types");

        // Customer.runtimeOnly is @Transient and should not produce an association.
        assertNull(findAssociationEndByName(model, "runtimeOnly"), "Did not expect an association end for transient fields");
    }

    private static Property findAssociationEndByName(Package pkg, String name) {
        for (Association assoc : collectAssociations(pkg)) {
            for (Property end : assoc.getMemberEnds()) {
                if (name.equals(end.getName())) return end;
            }
        }
        return null;
    }

    private static void assertOwnerEndIsExactlyOne(Property namedEnd) {
        assertNotNull(namedEnd);
        Association assoc = namedEnd.getAssociation();
        assertNotNull(assoc);
        Property opposite = null;
        for (Property p : assoc.getMemberEnds()) {
            if (p == null) continue;
            if (p == namedEnd) continue;
            opposite = p;
        }
        assertNotNull(opposite, "Expected an opposite association end");
        assertEquals(1, opposite.getLower(), "Owner end lower bound should be 1 for value-object containment");
        assertEquals(1, opposite.getUpper(), "Owner end upper bound should be 1 for value-object containment");
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

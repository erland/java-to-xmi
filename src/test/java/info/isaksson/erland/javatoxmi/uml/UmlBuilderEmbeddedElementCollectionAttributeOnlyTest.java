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

/**
 * Historical test name kept for compatibility when users unzip over an existing working copy.
 *
 * Behavior changed: @Embedded and @ElementCollection(of embeddable) now produce composition associations.
 * Element collections of basic types remain attribute-only, and @Transient fields do not produce associations.
 */
public class UmlBuilderEmbeddedElementCollectionAttributeOnlyTest {

    @Test
    void embeddedAndElementCollection_doNotCreateAssociationLines() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        Model model = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.RESOLVED).umlModel;

        Property addressEnd = findAssociationEndByName(model, "address");
        assertNotNull(addressEnd, "Expected Customer.address (@Embedded) to create an association end");
        assertEquals(AggregationKind.COMPOSITE_LITERAL, addressEnd.getAggregation(), "@Embedded should be composition");

        Property prevEnd = findAssociationEndByName(model, "previousAddresses");
        assertNotNull(prevEnd, "Expected Customer.previousAddresses (@ElementCollection Address) to create an association end");
        assertEquals(AggregationKind.COMPOSITE_LITERAL, prevEnd.getAggregation(), "@ElementCollection of embeddable should be composition");

        assertNull(findAssociationEndByName(model, "tags"), "Did not expect an association for element collection of basic types");
        assertNull(findAssociationEndByName(model, "runtimeOnly"), "Did not expect an association for transient fields");
    }

    private static Property findAssociationEndByName(Package pkg, String name) {
        for (Association assoc : collectAssociations(pkg)) {
            for (Property end : assoc.getMemberEnds()) {
                if (name.equals(end.getName())) return end;
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

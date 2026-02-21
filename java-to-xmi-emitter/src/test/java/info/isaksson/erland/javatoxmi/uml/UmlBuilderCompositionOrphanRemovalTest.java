package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.testutil.TestPaths;
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

public class UmlBuilderCompositionOrphanRemovalTest {

    @Test
    void orphanRemoval_oneToMany_marksCompositeAggregation() throws Exception {
        Path source = TestPaths.resolveInRepo("samples/mini/src/main/java");
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = new JavaExtractor().extract(source, files);

        Model model = new UmlBuilder().build(jModel, "MiniModel", true, AssociationPolicy.JPA_ONLY).umlModel;

        // Find an association end named "orders" and verify aggregation is composite.
        Property ordersEnd = findAssociationEndByName(model, "orders");
        assertNotNull(ordersEnd, "Expected an association end named 'orders'");
        assertEquals(AggregationKind.COMPOSITE_LITERAL, ordersEnd.getAggregation(), "orders end should be composite due to orphanRemoval=true");
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

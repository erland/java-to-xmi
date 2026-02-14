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

/**
 * Validates generic-aware association creation: List<LocalType> becomes an association to LocalType with 0..* multiplicity.
 */
public class UmlBuilderAssociationTest {

    @Test
    void listOfLocalTypeCreatesAssociationWithManyMultiplicity() throws Exception {
        Path source = Path.of("samples/mini/src/main/java");
        JavaExtractor extractor = new JavaExtractor();
        var files = SourceScanner.scan(source, List.of(), false);
        JModel jModel = extractor.extract(source, files);

        UmlBuilder builder = new UmlBuilder();
        UmlBuilder.Result result = builder.build(jModel, "MiniModel");
        Model model = result.umlModel;

        // Find classifiers (they live inside packages, not directly on the model)
        Classifier team = findClassifierByName(model, "Team");
        assertNotNull(team, "Expected Team classifier");

        Classifier greeter = findClassifierByName(model, "Greeter");
        assertNotNull(greeter, "Expected Greeter classifier");

        // There should exist an association between Team and Greeter created from field List<Greeter> greeters
        boolean found = false;
        for (Association a : collectAssociations(model)) {
            List<Property> ends = a.getMemberEnds();
            if (ends.size() != 2) continue;

            Property end1 = ends.get(0);
            Property end2 = ends.get(1);

            var t1 = end1.getType();
            var t2 = end2.getType();
            if (!(t1 instanceof Classifier) || !(t2 instanceof Classifier)) continue;

            Classifier c1 = (Classifier) t1;
            Classifier c2 = (Classifier) t2;

            if ((c1 == team && c2 == greeter) || (c1 == greeter && c2 == team)) {
                // Determine the end pointing to Greeter and assert multiplicity 0..*
                Property greeterEnd = (c1 == greeter) ? end1 : end2;
                assertEquals(0, greeterEnd.getLower(), "Greeter end lower multiplicity should be 0");
                assertEquals(-1, greeterEnd.getUpper(), "Greeter end upper multiplicity should be * (-1)");
                found = true;
                break;
            }
        }

        assertTrue(found, "Expected an association between Team and Greeter");
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

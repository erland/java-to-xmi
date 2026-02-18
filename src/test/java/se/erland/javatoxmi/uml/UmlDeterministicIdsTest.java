package se.erland.javatoxmi.uml;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Element;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 4 regression: every UML element should carry a deterministic java-to-xmi:id annotation,
 * so XMI IDs do not depend on traversal order (which can change when containment changes).
 */
public class UmlDeterministicIdsTest {

    @Test
    void allElementsHaveDeterministicIdAnnotation() throws Exception {
        Path root = Paths.get("samples/mini").toAbsolutePath().normalize();
        var javaFiles = SourceScanner.scan(root, Collections.emptyList(), true);
        JModel m = new JavaExtractor().extract(root, javaFiles);

        UmlBuilder.Result r = new UmlBuilder().build(m, "mini");
        assertNotNull(r.umlModel);

        // Root must have ID
        EAnnotation rootAnn = r.umlModel.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        assertNotNull(rootAnn, "Root model must have java-to-xmi:id annotation");
        assertNotNull(rootAnn.getDetails().get("id"));
        assertFalse(rootAnn.getDetails().get("id").isBlank());

        TreeIterator<EObject> it = r.umlModel.eAllContents();
        int checked = 0;
        while (it.hasNext()) {
            EObject obj = it.next();
            if (!(obj instanceof Element)) continue;
            Element el = (Element) obj;
            EAnnotation ann = el.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
            assertNotNull(ann, "Missing java-to-xmi:id annotation on " + obj.eClass().getName());
            String id = ann.getDetails().get("id");
            assertNotNull(id, "Missing id detail on " + obj.eClass().getName());
            assertFalse(id.isBlank(), "Blank id detail on " + obj.eClass().getName());
            checked++;
        }
        assertTrue(checked > 0, "Expected to check at least one contained element");
    }
}

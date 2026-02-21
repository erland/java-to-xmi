package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Type;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.JTypeKind;
import info.isaksson.erland.javatoxmi.model.JVisibility;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderTypeDocCommentTest {

    @Test
    void emitsOwnedCommentOnType_withDeterministicIdAnnotation() {
        JModel jm = new JModel(Paths.get("."), List.of());
        String doc = "Hello world!\n\nLine two.";
        JType t = new JType(
                "p",
                "C",
                "p.C",
                null,
                JTypeKind.CLASS,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                null,
                List.of(),
                List.of(),
                doc,
                List.of(),
                List.of(),
                List.of()
        );
        jm.types.add(t);

        UmlBuilder.Result r = new UmlBuilder().build(jm, "M", true);
        Model m = r.umlModel;

        Package p = m.getNestedPackage("p");
        assertNotNull(p);
        Type c = p.getOwnedType("C");
        assertNotNull(c);

        assertEquals(1, c.getOwnedComments().size());
        Comment cm = c.getOwnedComments().get(0);
        assertEquals(doc, cm.getBody());

        assertNotNull(cm.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE));
        assertTrue(cm.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE).getDetails().values().stream()
                .anyMatch(v -> v.contains("Comment:p.C")));
    }
}

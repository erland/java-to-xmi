package se.erland.javatoxmi.uml;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.Test;

public class JavaAnnotationProfileBuilderTest {

    @Test
    void profileBuilderCreatesProfileStereotypeAndAttributesWithDeterministicIds() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("TestModel");

        JavaAnnotationProfileBuilder b = new JavaAnnotationProfileBuilder();
        Profile p = b.ensureProfile(model);

        assertNotNull(p);
        assertEquals(JavaAnnotationProfileBuilder.PROFILE_NAME, p.getName());

        Stereotype st = b.ensureStereotype(p, "Table", "jakarta.persistence.Table");
        assertNotNull(st);
        assertEquals("Table", st.getName());

        // Qualified name metadata present
        assertEquals("jakarta.persistence.Table", getMeta(st, JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE, "qualifiedName"));

        // Attribute created
        Property prop = b.ensureStringAttribute(p, st, "name");
        assertNotNull(prop);
        assertEquals("name", prop.getName());

        // Deterministic id annotations
        assertNotNull(st.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE));
        assertNotNull(prop.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE));
        assertNotNull(p.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE));

        // Creating again should reuse
        Stereotype st2 = b.ensureStereotype(p, "Table", "jakarta.persistence.Table");
        assertSame(st, st2);
    }

    private static String getMeta(org.eclipse.uml2.uml.Element e, String source, String key) {
        EAnnotation ann = e.getEAnnotation(source);
        if (ann == null) return null;
        return ann.getDetails().get(key);
    }
}

package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Extension;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for the JavaAnnotations profile builder focusing on the tool-tag stereotype.
 *
 * These tests are meant to protect refactors of JavaAnnotationProfileBuilder and metaclass extension logic.
 */
public class JavaAnnotationProfileBuilderToolTagsTest {

    @Test
    void ensureProfile_alwaysCreatesToolTagsStereotypeWithAllAttributes() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("TestModel");

        JavaAnnotationProfileBuilder b = new JavaAnnotationProfileBuilder();
        Profile p = b.ensureProfile(model);

        assertNotNull(p);
        Stereotype tags = p.getOwnedStereotype(JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE);
        assertNotNull(tags, "Expected tool-tag stereotype to exist");

        Set<String> attrNames = new HashSet<>();
        for (Property prop : tags.getOwnedAttributes()) {
            if (prop != null && prop.getName() != null) attrNames.add(prop.getName());
        }

        for (String key : JavaAnnotationProfileBuilder.TOOL_TAG_KEYS) {
            assertTrue(attrNames.contains(key), "Expected tool-tag attribute: " + key);
        }
    }

    @Test
    void toolTagsStereotype_extendsNamedElement_viaExtension() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("TestModel");

        JavaAnnotationProfileBuilder b = new JavaAnnotationProfileBuilder();
        Profile p = b.ensureProfile(model);
        assertNotNull(p);

        Stereotype tags = p.getOwnedStereotype(JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE);
        assertNotNull(tags);

        // Find an Extension that involves both the tool stereotype and UML::NamedElement
        boolean hasNamedElementExtension = p.getOwnedTypes().stream()
                .filter(t -> t instanceof Extension)
                .map(t -> (Extension) t)
                .anyMatch(ext -> ext.getMemberEnds().stream().anyMatch(end -> end != null && end.getType() != null
                        && "NamedElement".equals(end.getType().getName()))
                        && ext.getMemberEnds().stream().anyMatch(end -> end != null && end.getType() == tags));

        assertTrue(hasNamedElementExtension, "Expected tool-tag stereotype to extend UML::NamedElement");
    }
}

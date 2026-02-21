package info.isaksson.erland.javatoxmi.uml;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Extension;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.Test;

public class JavaAnnotationProfileBuilderExtensionsTest {

    @Test
    void createsMetaclassExtensionsForClassInterfaceAndEnumeration() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("TestModel");

        JavaAnnotationProfileBuilder b = new JavaAnnotationProfileBuilder();
        Profile p = b.ensureProfile(model);

        Stereotype stClass = b.ensureStereotype(p, "Entity", "jakarta.persistence.Entity");
        Extension extClass = b.ensureMetaclassExtension(p, stClass, JavaAnnotationProfileBuilder.MetaclassTarget.CLASS);
        assertNotNull(extClass);
        assertTrue(extensionHasMetaclassEnd(extClass, "Class"), "Expected extension to reference UML::Class");
        assertHasDeterministicId(extClass);

        Stereotype stIface = b.ensureStereotype(p, "Api", "com.acme.Api");
        Extension extIface = b.ensureMetaclassExtension(p, stIface, JavaAnnotationProfileBuilder.MetaclassTarget.INTERFACE);
        assertNotNull(extIface);
        assertTrue(extensionHasMetaclassEnd(extIface, "Interface"), "Expected extension to reference UML::Interface");
        assertHasDeterministicId(extIface);

        Stereotype stEnum = b.ensureStereotype(p, "Marker", "com.acme.Marker");
        Extension extEnum = b.ensureMetaclassExtension(p, stEnum, JavaAnnotationProfileBuilder.MetaclassTarget.ENUMERATION);
        assertNotNull(extEnum);
        assertTrue(extensionHasMetaclassEnd(extEnum, "Enumeration"), "Expected extension to reference UML::Enumeration");
        assertHasDeterministicId(extEnum);

        // Re-ensuring should not duplicate extensions
        Extension extClass2 = b.ensureMetaclassExtension(p, stClass, JavaAnnotationProfileBuilder.MetaclassTarget.CLASS);
        assertSame(extClass, extClass2);
    }

    private static boolean extensionHasMetaclassEnd(Extension ext, String metaclassName) {
        return ext.getMemberEnds().stream()
                .anyMatch(p -> p != null && p.getType() != null && metaclassName.equals(p.getType().getName()));
    }

    private static void assertHasDeterministicId(org.eclipse.uml2.uml.Element e) {
        EAnnotation ann = e.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        assertNotNull(ann, "Expected deterministic id annotation");
        assertNotNull(ann.getDetails().get("id"), "Expected id detail");
    }
}

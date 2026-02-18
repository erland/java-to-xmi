package se.erland.javatoxmi.uml;

import java.util.Objects;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Extension;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;

/**
 * Profile builder for Java annotations and java-to-xmi tool metadata.
 *
 * <p>This class is intentionally a small facade coordinating two focused helpers:
 * {@link JavaAnnotationProfileStructureBuilder} (stereotype/attribute structure) and
 * {@link JavaAnnotationMetaclassExtensionHelper} (metamodel/metaclass references + extensions).
 */
public final class JavaAnnotationProfileBuilder {

    public static final String PROFILE_NAME = "JavaAnnotations";
    /**
     * Stable profile URI used for deterministic output. If left unset, UML2 may generate
     * a URI containing a random/UUID-like segment during Profile#define().
     */
    public static final String PROFILE_URI = "http://java-to-xmi/schemas/JavaAnnotations/1.0";
    public static final String JAVA_ANN_SOURCE = "java-to-xmi:java";

    /**
     * Stereotype used by java-to-xmi itself to persist additional mapping metadata
     * (e.g. collection element types, JPA/validation hints) in a tool-friendly way.
     */
    public static final String TOOL_TAGS_STEREOTYPE = "J2XTags";

    /** Tag keys emitted by MultiplicityResolver / UmlBuilder and persisted via {@link #TOOL_TAGS_STEREOTYPE}. */
    public static final String[] TOOL_TAG_KEYS = new String[] {
            "isArray",
            "containerKind",
            "collectionKind",
            "elementType",
            "mapKeyType",
            "mapValueType",
            "jpaRelation",
            "relationSource",
            "aggregation",
            "jpaOrphanRemoval",
            "nullableSource",
            "validationSizeMin",
            "validationSizeMax"
    };

    /** The UML metaclass that a stereotype should extend for MVP. */
    public enum MetaclassTarget {
        CLASS,
        INTERFACE,
        ENUMERATION
    }

    private final JavaAnnotationProfileStructureBuilder structure = new JavaAnnotationProfileStructureBuilder();
    private final JavaAnnotationMetaclassExtensionHelper metaclasses = new JavaAnnotationMetaclassExtensionHelper();

    /** Ensure the JavaAnnotations profile exists under the given UML model. */
    public Profile ensureProfile(Model model) {
        Objects.requireNonNull(model, "model");
        Profile profile = structure.ensureProfile(model);
        // Ensure tool-tags stereotype extends NamedElement so it can be applied broadly.
        Stereotype tags = profile.getOwnedStereotype(TOOL_TAGS_STEREOTYPE);
        if (tags != null) {
            metaclasses.ensureStereotypeExtendsMetaclass(profile, tags, "NamedElement", false);
        }
        return profile;
    }

    /** Ensure a stereotype exists for an annotation. */
    public Stereotype ensureStereotype(Profile profile, String simpleName, String qualifiedName) {
        return structure.ensureStereotype(profile, simpleName, qualifiedName);
    }

    /** Ensure a String-typed attribute exists on the stereotype. */
    public Property ensureStringAttribute(Profile profile, Stereotype st, String attrName) {
        return structure.ensureStringAttribute(profile, st, attrName);
    }

    /** Ensure the stereotype extends a specific UML metaclass (Class/Interface/Enumeration). */
    public Extension ensureMetaclassExtension(Profile profile, Stereotype st, MetaclassTarget target) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(st, "st");
        Objects.requireNonNull(target, "target");

        final String metaclassName;
        switch (target) {
            case CLASS:
                metaclassName = "Class";
                break;
            case INTERFACE:
                metaclassName = "Interface";
                break;
            case ENUMERATION:
                metaclassName = "Enumeration";
                break;
            default:
                throw new IllegalArgumentException("Unsupported metaclass target: " + target);
        }
        return metaclasses.ensureStereotypeExtendsMetaclass(profile, st, metaclassName, false);
    }

    // ---------------------------------------------------------------------
    // Shared utilities used by helpers (package-private on purpose)
    // ---------------------------------------------------------------------

    static void annotateIdIfMissing(Element element, String key) {
        if (element == null) return;
        EAnnotation ann = element.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        if (ann != null && ann.getDetails().containsKey("id")) return;
        String id = UmlIdStrategy.id(key);
        if (ann == null) {
            ann = element.createEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        }
        ann.getDetails().put("id", id);
        ann.getDetails().put("key", key);
    }
}

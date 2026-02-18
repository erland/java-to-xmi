package se.erland.javatoxmi.uml;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.ElementImport;
import org.eclipse.uml2.uml.ExtensionEnd;
import org.eclipse.uml2.uml.Extension;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PackageImport;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.resource.UMLResource;

/**
 * Step 3 — Profile builder for Java annotations.
 *
 * MVP responsibility:
 * - Ensure a Profile exists in the UML Model
 * - Create/reuse stereotypes representing Java annotations
 * - Create/reuse stereotype attributes (tag definitions) as String
 * - Attach deterministic IDs and store qualified-name metadata
 *
 * NOTE: Step 3 did not create metaclass Extensions nor apply the profile.
 * Step 4 adds metaclass Extensions.
 *
 * Compatibility note:
 * Eclipse UML2 3.1 is very strict about Profile/metamodel/metaclass identity when calling
 * {@code Stereotype#createExtension}. In practice (especially with in-memory models used in tests)
 * this may throw {@link IllegalArgumentException} even when references look correct. For that case
 * we fall back to manual creation of the {@link Extension} association.
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
            "nullableSource",
            "validationSizeMin",
            "validationSizeMax"
    };

    // Tracks used stereotype names to avoid collisions.
    private final Map<String, String> stereotypeNameToQualified = new HashMap<>();

    /** The UML metaclass that a stereotype should extend for MVP. */
    public enum MetaclassTarget {
        CLASS,
        INTERFACE,
        ENUMERATION
    }

    /** Ensure the JavaAnnotations profile exists under the given UML model. */
    public Profile ensureProfile(Model model) {
        Objects.requireNonNull(model, "model");
        // Find existing
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && PROFILE_NAME.equals(p.getName())) {
                annotateIdIfMissing(p, "Profile:" + PROFILE_NAME);
                ensureStringPrimitive(p);
                indexExistingStereotypes(p);
                ensureToolTagsStereotype(p);
                return p;
            }
        }
        Profile profile = UMLFactory.eINSTANCE.createProfile();
        profile.setName(PROFILE_NAME);
        model.getPackagedElements().add(profile);
        annotateIdIfMissing(profile, "Profile:" + PROFILE_NAME);
        ensureStringPrimitive(profile);
        indexExistingStereotypes(profile);
        ensureToolTagsStereotype(profile);
        return profile;
    }

    /** Ensure the tool metadata stereotype exists (and extends NamedElement) so tags can be applied to many UML elements. */
    private void ensureToolTagsStereotype(Profile profile) {
        if (profile == null) return;
        Stereotype st = profile.getOwnedStereotype(TOOL_TAGS_STEREOTYPE);
        if (st == null) {
            st = profile.createOwnedStereotype(TOOL_TAGS_STEREOTYPE, false);
            annotateIdIfMissing(st, "Stereotype:" + PROFILE_NAME + "#" + TOOL_TAGS_STEREOTYPE);
        }

        // Ensure attributes exist as Strings.
        for (String key : TOOL_TAG_KEYS) {
            if (key == null || key.isBlank()) continue;
            ensureStringAttribute(profile, st, key);
        }

        // Extend NamedElement so we can apply to Property/Operation/Parameter/Association/etc.
        org.eclipse.uml2.uml.Class metaclass = ensureMetaclassReference(profile, "NamedElement");
        if (metaclass != null) {
            ensureExtension(profile, st, metaclass, false);
        }
    }

    /**
     * Ensure a stereotype exists for an annotation.
     *
     * @param profile The profile
     * @param simpleName The annotation simple name (e.g. Entity)
     * @param qualifiedName Optional qualified name (e.g. jakarta.persistence.Entity)
     */
    public Stereotype ensureStereotype(Profile profile, String simpleName, String qualifiedName) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(simpleName, "simpleName");

        String desiredName = sanitizeUmlName(simpleName);
        String qn = qualifiedName;

        // If stereotype already exists with matching qualifiedName, reuse.
        for (Stereotype st : profile.getOwnedStereotypes()) {
            if (desiredName.equals(st.getName())) {
                String existingQn = getQualifiedNameMeta(st);
                if (Objects.equals(existingQn, qn) || (existingQn == null && qn == null)) {
                    annotateIdIfMissing(st, stereotypeIdKey(qn, desiredName));
                    return st;
                }
            }
        }

        // Collision handling: if name exists but qualified differs, suffix deterministically.
        String finalName = desiredName;
        if (nameTakenByDifferentQualified(profile, finalName, qn)) {
            finalName = desiredName + "__" + sanitizeSuffixFromQualified(qn);
            int i = 2;
            while (nameTakenByDifferentQualified(profile, finalName, qn) || hasStereotypeNamed(profile, finalName)) {
                finalName = desiredName + "__" + sanitizeSuffixFromQualified(qn) + "_" + i;
                i++;
            }
        }

        Stereotype st = profile.createOwnedStereotype(finalName, false);
        setQualifiedNameMeta(st, qn);
        annotateIdIfMissing(st, stereotypeIdKey(qn, finalName));
        stereotypeNameToQualified.put(finalName, qn);
        return st;
    }

    /** Ensure a String-typed attribute exists on the stereotype. */
    public Property ensureStringAttribute(Profile profile, Stereotype st, String attrName) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(st, "st");
        Objects.requireNonNull(attrName, "attrName");

        String name = sanitizeUmlName(attrName);
        for (Property p : st.getOwnedAttributes()) {
            if (name.equals(p.getName())) {
                annotateIdIfMissing(p, "StereotypeAttr:" + st.getName() + "#" + name);
                return p;
            }
        }
        PrimitiveType stringType = ensureStringPrimitive(profile);
        Property p = st.createOwnedAttribute(name, stringType);
        annotateIdIfMissing(p, "StereotypeAttr:" + st.getName() + "#" + name);
        return p;
    }

    /**
     * Step 4 — Ensure the stereotype extends a specific UML metaclass (Class/Interface/Enumeration).
     *
     * This creates a metaclass reference in the profile (if needed) and an Extension association
     * from the stereotype to that metaclass.
     */
    public Extension ensureMetaclassExtension(Profile profile, Stereotype st, MetaclassTarget target) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(st, "st");
        Objects.requireNonNull(target, "target");

        org.eclipse.uml2.uml.Class metaclass;
        switch (target) {
            case CLASS:
                metaclass = ensureMetaclassReference(profile, "Class");
                break;
            case INTERFACE:
                metaclass = ensureMetaclassReference(profile, "Interface");
                break;
            case ENUMERATION:
                metaclass = ensureMetaclassReference(profile, "Enumeration");
                break;
            default:
                throw new IllegalArgumentException("Unsupported metaclass target: " + target);
        }

        // Avoid duplicates: UML2 3.1 sometimes doesn't maintain Stereotype#getExtensions reliably
        // for manually-constructed extensions, so we also scan the Profile's packaged elements.
        Extension existing = findExistingExtension(profile, st, metaclass);
        if (existing != null) {
            annotateIdIfMissing(existing, "Extension:" + st.getName() + "->" + metaclass.getName());
            for (Property p : existing.getOwnedEnds()) {
                if (p == null) continue;
                annotateIdIfMissing(p, "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
            }
            return existing;
        }

        Extension ext;
        try {
            // UML2 3.1 can be extremely strict about profile/metaclass identity and may throw
            // IllegalArgumentException even when references look correct. Fall back to manual
            // construction in that case.
            ext = st.createExtension(metaclass, false);
        } catch (IllegalArgumentException ex) {
            ext = createExtensionManually(profile, st, metaclass, false);
        }
        annotateIdIfMissing(ext, "Extension:" + st.getName() + "->" + metaclass.getName());
        for (Property p : ext.getMemberEnds()) {
            if (p == null) continue;
            annotateIdIfMissing(p, "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
        }
        return ext;
    }

    /**
     * Ensure the given stereotype has an Extension to the provided metaclass reference.
     *
     * <p>This follows the same defensive strategy as {@link #ensureMetaclassExtension(Profile, Stereotype, MetaclassTarget)}
     * and falls back to manual Extension construction when UML2 refuses {@code createExtension(...)}.</p>
     */
    private static Extension ensureExtension(Profile profile, Stereotype st, org.eclipse.uml2.uml.Class metaclass, boolean required) {
        if (profile == null || st == null || metaclass == null) return null;

        Extension existing = findExistingExtension(profile, st, metaclass);
        if (existing != null) {
            annotateIdIfMissing(existing, "Extension:" + st.getName() + "->" + metaclass.getName());
            for (Property p : existing.getOwnedEnds()) {
                if (p == null) continue;
                annotateIdIfMissing(p, "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
            }
            return existing;
        }

        Extension ext;
        try {
            ext = st.createExtension(metaclass, required);
        } catch (IllegalArgumentException ex) {
            ext = createExtensionManually(profile, st, metaclass, required);
        }

        annotateIdIfMissing(ext, "Extension:" + st.getName() + "->" + metaclass.getName());
        for (Property p : ext.getOwnedEnds()) {
            if (p == null) continue;
            annotateIdIfMissing(p, "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
        }
        return ext;
    }

    /**
     * Manual construction of an Extension association (fallback when UML2 refuses createExtension).
     * Produces valid XMI for most UML tools and avoids version-specific precondition checks.
     */
    private static Extension createExtensionManually(Profile profile, Stereotype st, org.eclipse.uml2.uml.Class metaclass, boolean required) {
        Extension ext = UMLFactory.eINSTANCE.createExtension();
        ext.setName(st.getName() + "_extends_" + metaclass.getName());

        // Metaclass end ("base_<Metaclass>")
        // NOTE: In UML2 3.1, Extension::ownedEnd is typed as ExtensionEnd (not Property).
        // ExtensionEnd is a subclass of Property, so we use ExtensionEnd for BOTH ends.
        ExtensionEnd baseEnd = UMLFactory.eINSTANCE.createExtensionEnd();
        baseEnd.setName("base_" + metaclass.getName());
        baseEnd.setType(metaclass);
        // UML2 3.1 Extension does not expose setRequired(boolean). Approximate the
        // required-ness by setting the lower bound on the base end.
        baseEnd.setLower(required ? 1 : 0);
        baseEnd.setUpper(1);
        baseEnd.setAggregation(AggregationKind.NONE_LITERAL);
        baseEnd.setAssociation(ext);

        // Stereotype end (ExtensionEnd, composite)
        ExtensionEnd stereoEnd = UMLFactory.eINSTANCE.createExtensionEnd();
        stereoEnd.setName(st.getName());
        stereoEnd.setType(st);
        stereoEnd.setLower(0);
        stereoEnd.setUpper(1);
        stereoEnd.setAggregation(AggregationKind.COMPOSITE_LITERAL);
        stereoEnd.setAssociation(ext);

        ext.getOwnedEnds().add(baseEnd);
        ext.getOwnedEnds().add(stereoEnd);
        // memberEnd is a superset/derived view; ownedEnd is the safe containment.
        // Adding to memberEnds is not required and can cause EMF subset/superset issues
        // depending on UML2 version, so we avoid it.

        // Ensure the extension is contained in the profile so it serializes.
        profile.getPackagedElements().add(ext);
        return ext;
    }

    // --- helpers ---

    private void indexExistingStereotypes(Profile profile) {
        for (Stereotype st : profile.getOwnedStereotypes()) {
            stereotypeNameToQualified.put(st.getName(), getQualifiedNameMeta(st));
        }
    }

    private boolean hasStereotypeNamed(Profile profile, String name) {
        for (Stereotype st : profile.getOwnedStereotypes()) {
            if (name.equals(st.getName())) return true;
        }
        return false;
    }

    private boolean nameTakenByDifferentQualified(Profile profile, String name, String qualifiedName) {
        for (Stereotype st : profile.getOwnedStereotypes()) {
            if (name.equals(st.getName())) {
                String existingQn = getQualifiedNameMeta(st);
                return !Objects.equals(existingQn, qualifiedName);
            }
        }
        return false;
    }

    private static String stereotypeIdKey(String qualifiedName, String stereotypeName) {
        String qn = qualifiedName == null ? "" : qualifiedName;
        return "Stereotype:" + qn + "#" + stereotypeName;
    }

    private static void annotateIdIfMissing(Element element, String key) {
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

    private static PrimitiveType ensureStringPrimitive(Profile profile) {
        // Many UML tools expect "String" as a primitive type.
        // IMPORTANT: UmlBuilder already creates a global PrimitiveType "String" in the model's
        // "_primitives" package (deterministic id key "Primitive:String"). If we create another
        // one inside the profile, we'd end up with duplicate xmi:id values in the same document.

        // 1) Prefer an existing global "String" primitive anywhere in the enclosing UML Model.
        // NOTE: Profile#getNearestPackage() will often return the profile itself (because Profile is a Package),
        // so searching from there can miss the model's "_primitives" package and lead to duplicates.

        org.eclipse.uml2.uml.Package searchRoot = profile.getModel();
        if (searchRoot == null) {
            // Fallback: walk up until we find a Model or top-level Package.
            org.eclipse.emf.ecore.EObject cur = profile;
            while (cur != null) {
                if (cur instanceof org.eclipse.uml2.uml.Model m) {
                    searchRoot = m;
                    break;
                }
                if (cur instanceof org.eclipse.uml2.uml.Package p) {
                    searchRoot = p;
                }
                cur = cur.eContainer();
            }
        }

        if (searchRoot != null) {
            PrimitiveType existing = findPrimitiveTypeByName(searchRoot, "String");
            if (existing != null) {
                annotateIdIfMissing(existing, "Primitive:String");
                return existing;
            }
        }

        // 2) As a last resort, create the primitive in the model-level "_primitives" package (NOT inside the profile).
        org.eclipse.uml2.uml.Package owner = searchRoot != null ? searchRoot : profile;
        org.eclipse.uml2.uml.Package primitivesPkg = findOrCreateChildPackage(owner, "_primitives");

        PrimitiveType pt = UMLFactory.eINSTANCE.createPrimitiveType();
        pt.setName("String");
        primitivesPkg.getPackagedElements().add(pt);
        annotateIdIfMissing(pt, "Primitive:String");
        return pt;
    }

    private static org.eclipse.uml2.uml.Package findOrCreateChildPackage(org.eclipse.uml2.uml.Package owner, String name) {
        if (owner == null) return null;
        for (PackageableElement pe : owner.getPackagedElements()) {
            if (pe instanceof org.eclipse.uml2.uml.Package p && name.equals(p.getName())) {
                return p;
            }
        }
        org.eclipse.uml2.uml.Package p = UMLFactory.eINSTANCE.createPackage();
        p.setName(name);
        owner.getPackagedElements().add(p);
        // best-effort deterministic id annotation
        annotateIdIfMissing(p, "Package:" + name);
        return p;
    }

    private static PrimitiveType findPrimitiveTypeByName(org.eclipse.uml2.uml.Package pkg, String name) {
        if (pkg == null) return null;
        for (PackageableElement pe : pkg.getPackagedElements()) {
            if (pe instanceof PrimitiveType pt && name.equals(pt.getName())) {
                return pt;
            }
            if (pe instanceof org.eclipse.uml2.uml.Package nested) {
                PrimitiveType found = findPrimitiveTypeByName(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static org.eclipse.uml2.uml.Class ensureMetaclassReference(Profile profile, String metaclassName) {
        // UML2 3.1 is strict: the metaclass passed to Stereotype#createExtension(...) must be a metaclass
        // that the Profile references (via metaclassReference) AND the Profile must reference the UML metamodel
        // (via metamodelReference). If these references are missing, createExtension throws IllegalArgumentException.
        ResourceSet rs = ensureResourceSetFor(profile);

        // Load UML metamodel into the SAME ResourceSet as the Profile/Model.
        // Always start from the metaclass instance loaded from the UML metamodel resource.
        // In UML2 3.1, Profile#createMetaclassReference(...) and Stereotype#createExtension(...) are
        // very strict about identity and expect the metaclass to come from the referenced metamodel.
        PackageableElement rawPe = UmlMetamodelCache.getMetaclass(rs, metaclassName);
        if (rawPe == null) {
            throw new IllegalStateException("Unable to locate UML metaclass '" + metaclassName + "'.");
        }
        if (!(rawPe instanceof org.eclipse.uml2.uml.Class)) {
            throw new IllegalStateException("UML metaclass '" + metaclassName + "' is not a UML Class element: " + rawPe.getClass().getName());
        }
        org.eclipse.uml2.uml.Class rawMetaclass = (org.eclipse.uml2.uml.Class) rawPe;

        org.eclipse.uml2.uml.Package rawPkg = rawMetaclass.getNearestPackage();
        if (rawPkg == null) {
            rawPkg = (org.eclipse.uml2.uml.Package) UmlMetamodelCache.getUmlMetamodel(rs);
        }

        // Ensure the profile references the metamodel package that owns this metaclass.
        ensureMetamodelReference(profile, rawPkg);

        // Use the raw metaclass instance from the metamodel for the metaclass reference creation.
        // After defining the profile, we'll resolve the "referenced metaclass" instance and use that.
        org.eclipse.uml2.uml.Class metaclass = rawMetaclass;

        // Ensure a metaclass reference import exists.
        ElementImport ei = findMetaclassImport(profile, metaclassName);
        if (ei == null) {
            try {
                // Return type differs across UML2 versions; in UML2 3.1 it is typically ElementImport.
                Object created = profile.createMetaclassReference(metaclass);
                if (created instanceof ElementImport) {
                    ei = (ElementImport) created;
                }
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to create metaclass reference for '" + metaclassName + "'.", t);
            }
        }
        if (ei == null) {
            // As a fallback, try to find it again (some UML2 versions don't return it directly).
            ei = findMetaclassImport(profile, metaclassName);
        }
        if (ei != null) {
            annotateIdIfMissing(ei, "MetaclassRefImport:" + metaclassName);
        }

        // UML2 3.1 is picky: createExtension(...) will only accept a metaclass that the Profile considers
        // a "referenced metaclass". That list is reliably populated once the Profile is defined.
        // Defining more than once is fine; define() is idempotent in UML2.
        ensureProfileDefined(profile);

        org.eclipse.uml2.uml.Class referenced = tryGetReferencedMetaclass(profile, metaclassName);
        if (referenced != null) {
            annotateIdIfMissing(referenced, "Metaclass:" + metaclassName);
            return referenced;
        }

        // If the referenced list is not available, fall back to the imported element from the metaclass reference.
        if (ei != null && ei.getImportedElement() instanceof org.eclipse.uml2.uml.Class c) {
            annotateIdIfMissing(c, "Metaclass:" + metaclassName);
            return c;
        }

        // Last resort: return the raw metaclass.
        annotateIdIfMissing(metaclass, "Metaclass:" + metaclassName);
        return metaclass;
    }

    private static void ensureProfileDefined(Profile profile) {
        try {
            java.lang.reflect.Method isDefined = profile.getClass().getMethod("isDefined");
            Object r = isDefined.invoke(profile);
            boolean defined = (r instanceof Boolean b) ? b : false;
            if (!defined) {
                java.lang.reflect.Method define = profile.getClass().getMethod("define");
                define.invoke(profile);
                normalizeDefinedProfile(profile);
            }
        } catch (NoSuchMethodException nsme) {
            // Older/newer variants may not expose isDefined/define the same way.
        } catch (Throwable t) {
            // If define fails, extension creation will not work. Surface the root cause.
            throw new IllegalStateException("Profile.define() failed; cannot create metaclass extensions.", t);
        }
    }

    /**
     * After Profile#define(), UML2 materializes an Ecore representation into an EAnnotation.
     * Ensure the EPackage nsURI is stable for deterministic XMI output.
     */
    private static void normalizeDefinedProfile(Profile profile) {
        if (profile == null) return;
        final String desiredUri = PROFILE_URI;

        try {
            for (EAnnotation ea : profile.getEAnnotations()) {
                if (ea == null) continue;
                // UML2 stores the generated Ecore package under this well-known source.
                if (!"http://www.eclipse.org/uml2/2.0.0/UML".equals(ea.getSource())) continue;
                for (EObject c : ea.getContents()) {
                    if (c instanceof org.eclipse.emf.ecore.EPackage ep) {
                        if (!Objects.equals(ep.getNsURI(), desiredUri)) {
                            ep.setNsURI(desiredUri);
                        }
                        if (ep.getNsPrefix() == null || ep.getNsPrefix().isBlank()) {
                            ep.setNsPrefix(PROFILE_NAME);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Best-effort only.
        }
    }

    private static ElementImport findMetaclassImport(Profile profile, String metaclassName) {
        try {
            for (ElementImport ei : profile.getMetaclassReferences()) {
                if (ei == null) continue;
                if (ei.getImportedElement() instanceof org.eclipse.uml2.uml.Class c && metaclassName.equals(c.getName())) {
                    return ei;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static PackageImport ensureMetamodelReference(Profile profile, org.eclipse.uml2.uml.Package umlMetamodel) {
        if (umlMetamodel == null) return null;
        // In UML2 3.1 the feature is metamodelReference : PackageImport[*].
        try {
            java.lang.reflect.Method getRefs = profile.getClass().getMethod("getMetamodelReferences");
            Object r = getRefs.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof org.eclipse.uml2.uml.PackageImport pi) {
                        if (pi.getImportedPackage() == umlMetamodel) return pi;
                        if (pi.getImportedPackage() != null && pi.getImportedPackage().eResource() != null && umlMetamodel.eResource() != null) {
                            if (Objects.equals(pi.getImportedPackage().eResource().getURI(), umlMetamodel.eResource().getURI())) return pi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method create = profile.getClass().getMethod("createMetamodelReference", org.eclipse.uml2.uml.Package.class);
            Object created = create.invoke(profile, umlMetamodel);
            if (created instanceof PackageImport pi) {
                annotateIdIfMissing(pi, "MetamodelRef:" + (umlMetamodel.getName() == null ? "UML" : umlMetamodel.getName()));
                return pi;
            } else if (created instanceof org.eclipse.uml2.uml.Element e) {
                annotateIdIfMissing(e, "MetamodelRef:" + (umlMetamodel.getName() == null ? "UML" : umlMetamodel.getName()));
            }
        } catch (Throwable ignored) {
            // Some UML2 variants may not require this, but UML2 3.1 does; if missing, extension creation will fail.
        }

        // Try once more to find the import we just created
        try {
            java.lang.reflect.Method getRefs = profile.getClass().getMethod("getMetamodelReferences");
            Object r = getRefs.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof PackageImport pi) {
                        if (pi.getImportedPackage() == umlMetamodel) return pi;
                        if (pi.getImportedPackage() != null && pi.getImportedPackage().eResource() != null && umlMetamodel.eResource() != null) {
                            if (Objects.equals(pi.getImportedPackage().eResource().getURI(), umlMetamodel.eResource().getURI())) return pi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static org.eclipse.uml2.uml.Class tryGetReferencedMetaclass(Profile profile, String metaclassName) {
        try {
            java.lang.reflect.Method m = profile.getClass().getMethod("getReferencedMetaclasses");
            Object r = m.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof org.eclipse.uml2.uml.Class c && metaclassName.equals(c.getName())) {
                        return c;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ResourceSet ensureResourceSetFor(Element element) {
        // Ensure the root Model is attached to a ResourceSet; profile/metaclass imports rely on this.
        Model m = (element instanceof Model) ? (Model) element : element.getModel();
        if (m != null && m.eResource() != null && m.eResource().getResourceSet() != null) {
            ResourceSet rs = m.eResource().getResourceSet();
            registerUmlResourceInfrastructure(rs);
            return rs;
        }

        ResourceSet rs = new ResourceSetImpl();
        registerUmlResourceInfrastructure(rs);
        Resource r = new XMIResourceImpl(URI.createURI("urn:java-to-xmi:model.uml"));
        rs.getResources().add(r);
        if (m != null) {
            r.getContents().add(m);
        }
        return rs;
    }

    private static void registerUmlResourceInfrastructure(ResourceSet rs) {
        // Basic registrations so UML resources can be parsed/serialized
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);
        rs.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);

        // If the optional "uml.resources" bundle is on the classpath (it is in our Maven setup),
        // initialize PATHMAP URI mappings so that UMLResource.UML_METAMODEL_URI can be resolved.
        // Use reflection so we don't hard-require the class at compile time across UML2 variants.
        try {
            Class<?> util = Class.forName("org.eclipse.uml2.uml.resources.util.UMLResourcesUtil");
            java.lang.reflect.Method init = util.getMethod("init", ResourceSet.class);
            init.invoke(null, rs);
        } catch (Throwable ignored) {
            // Safe to ignore: we'll fall back to direct URI loads and/or already-configured mappings.
        }
    }

    private static final class UmlMetamodelCache {
        private static final WeakHashMap<ResourceSet, Model> byResourceSet = new WeakHashMap<>();

        static Model getUmlMetamodel(ResourceSet rs) {
            synchronized (byResourceSet) {
                Model cached = byResourceSet.get(rs);
                if (cached != null) return cached;

                registerUmlResourceInfrastructure(rs);
                Resource r = tryLoadUmlMetamodel(rs);
                if (r == null || r.getContents().isEmpty()) {
                    throw new IllegalStateException("Failed to load UML metamodel resource.");
                }

                Object root = EcoreUtil.getObjectByType(r.getContents(), UMLPackage.Literals.MODEL);
                final Model m;
                if (root instanceof Model) {
                    m = (Model) root;
                } else {
                    Object first = r.getContents().get(0);
                    if (first instanceof Model) {
                        m = (Model) first;
                    } else {
                        throw new IllegalStateException("Unexpected UML metamodel root type: " + (first == null ? "null" : first.getClass().getName()));
                    }
                }
                byResourceSet.put(rs, m);
                return m;
            }
        }

        private static Resource tryLoadUmlMetamodel(ResourceSet rs) {
            // With org.eclipse.uml2.uml.resources on the classpath, the PATHMAP URI should work.
            try {
                return rs.getResource(URI.createURI(UMLResource.UML_METAMODEL_URI), true);
            } catch (Throwable ignored) {
                // Fall back to loading the resource directly from the classpath.
                // Verified for this project:
                //   org.eclipse.uml2.uml.resources contains: metamodels/UML.metamodel.uml
                try {
                    ClassLoader cl = JavaAnnotationProfileBuilder.class.getClassLoader();
                    String[] candidates = new String[] {"metamodels/UML.metamodel.uml", "UML.metamodel.uml"};
                    for (String p : candidates) {
                        java.net.URL url = cl.getResource(p);
                        if (url == null) continue;
                        // EMF can load from jar:file:... URLs via URI.
                        URI uri = URI.createURI(url.toString());
                        return rs.getResource(uri, true);
                    }
                } catch (Throwable ignored2) {
                    // ignore
                }
                return null;
            }
        }

        static PackageableElement getMetaclass(ResourceSet rs, String metaclassName) {
            Model m = getUmlMetamodel(rs);
            // Quick scan
            for (PackageableElement pe : m.getPackagedElements()) {
                if (metaclassName.equals(pe.getName())) return pe;
            }
            // Deep scan
            for (PackageableElement pe : m.getPackagedElements()) {
                if (pe instanceof org.eclipse.uml2.uml.Package p) {
                    PackageableElement found = deepFind(p, metaclassName);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static PackageableElement deepFind(org.eclipse.uml2.uml.Package pkg, String name) {
            for (PackageableElement pe : pkg.getPackagedElements()) {
                if (name.equals(pe.getName())) return pe;
                if (pe instanceof org.eclipse.uml2.uml.Package p2) {
                    PackageableElement r = deepFind(p2, name);
                    if (r != null) return r;
                }
            }
            for (org.eclipse.uml2.uml.Package p : pkg.getNestedPackages()) {
                PackageableElement r = deepFind(p, name);
                if (r != null) return r;
            }
            return null;
        }

    }
    private static void setQualifiedNameMeta(Stereotype st, String qualifiedName) {
        EAnnotation ann = st.getEAnnotation(JAVA_ANN_SOURCE);
        if (ann == null) ann = st.createEAnnotation(JAVA_ANN_SOURCE);
        if (qualifiedName != null) {
            ann.getDetails().put("qualifiedName", qualifiedName);
        }
    }

    private static String getQualifiedNameMeta(Stereotype st) {
        EAnnotation ann = st.getEAnnotation(JAVA_ANN_SOURCE);
        if (ann == null) return null;
        return ann.getDetails().get("qualifiedName");
    }

    private static String sanitizeSuffixFromQualified(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) return "unknown";
        // Remove the simple name tail if present
        String q = qualifiedName;
        int lastDot = q.lastIndexOf('.');
        if (lastDot >= 0) q = q.substring(0, lastDot);
        q = q.replace('.', '_');
        q = sanitizeUmlName(q);
        if (q.isBlank()) return "unknown";
        return q;
    }

    private static String sanitizeUmlName(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        // Replace illegal chars with '_'
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            sb.append(ok ? c : '_');
        }
        String out = sb.toString();
        // UML names typically shouldn't start with a digit.
        if (!out.isEmpty() && Character.isDigit(out.charAt(0))) {
            out = "_" + out;
        }
        return out;
    }
    /**
     * Find an already-existing Extension between the given stereotype and metaclass.
     *
     * Needed because UML2 3.1 can fail to expose manually-created extensions via
     * Stereotype#getExtensions until the profile is defined/serialized.
     */
    private static Extension findExistingExtension(Profile profile, Stereotype st, org.eclipse.uml2.uml.Class metaclass) {
        if (profile == null || st == null || metaclass == null) return null;

        // 1) Scan profile packaged elements (works for both native and manual extensions)
        for (org.eclipse.uml2.uml.PackageableElement pe : profile.getPackagedElements()) {
            if (!(pe instanceof Extension)) continue;
            Extension ext = (Extension) pe;
            if (extensionMatches(ext, st, metaclass)) return ext;
        }

        // 2) Fallback: scan stereotype extensions (may be empty/derived in some UML2 setups)
        for (Extension ext : st.getExtensions()) {
            if (ext == null) continue;
            if (extensionMatches(ext, st, metaclass)) return ext;
        }
        return null;
    }

    private static boolean extensionMatches(Extension ext, Stereotype st, org.eclipse.uml2.uml.Class metaclass) {
        if (ext == null) return false;
        boolean hasStereoEnd = false;
        boolean hasMetaEnd = false;

        for (Property p : ext.getOwnedEnds()) {
            if (p == null || p.getType() == null) continue;
            if (p.getType() == st) hasStereoEnd = true;
            if (p.getType() == metaclass) hasMetaEnd = true;
        }

        // In some cases the metaclass instance differs but name matches (e.g. different metamodel instance).
        // Accept name match only if metaclass instance match wasn't found.
        if (!hasMetaEnd && metaclass.getName() != null) {
            for (Property p : ext.getOwnedEnds()) {
                if (p == null || p.getType() == null) continue;
                if (metaclass.getName().equals(p.getType().getName())) {
                    hasMetaEnd = true;
                    break;
                }
            }
        }

        return hasStereoEnd && hasMetaEnd;
    }
}
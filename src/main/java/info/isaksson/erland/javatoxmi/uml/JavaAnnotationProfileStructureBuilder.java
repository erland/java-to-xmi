package info.isaksson.erland.javatoxmi.uml;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;

/**
 * Builds the structural parts of the JavaAnnotations profile:
 * profile element, stereotypes, and stereotype attributes.
 */
final class JavaAnnotationProfileStructureBuilder {

    // Tracks used stereotype names to avoid collisions.
    private final Map<String, String> stereotypeNameToQualified = new HashMap<>();

    /** Ensure the JavaAnnotations profile exists under the given UML model. */
    Profile ensureProfile(Model model) {
        Objects.requireNonNull(model, "model");
        // Find existing
        for (PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && JavaAnnotationProfileBuilder.PROFILE_NAME.equals(p.getName())) {
                JavaAnnotationProfileBuilder.annotateIdIfMissing(p, "Profile:" + JavaAnnotationProfileBuilder.PROFILE_NAME);
                ensureStringPrimitive(p);
                indexExistingStereotypes(p);
                ensureToolTagsStereotype(p);
                return p;
            }
        }
        Profile profile = UMLFactory.eINSTANCE.createProfile();
        profile.setName(JavaAnnotationProfileBuilder.PROFILE_NAME);
        model.getPackagedElements().add(profile);
        JavaAnnotationProfileBuilder.annotateIdIfMissing(profile, "Profile:" + JavaAnnotationProfileBuilder.PROFILE_NAME);
        ensureStringPrimitive(profile);
        indexExistingStereotypes(profile);
        ensureToolTagsStereotype(profile);
        return profile;
    }

    /** Ensure the tool metadata stereotype exists and has all expected String attributes. */
    private void ensureToolTagsStereotype(Profile profile) {
        if (profile == null) return;
        Stereotype st = profile.getOwnedStereotype(JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE);
        if (st == null) {
            st = profile.createOwnedStereotype(JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE, false);
            JavaAnnotationProfileBuilder.annotateIdIfMissing(st,
                    "Stereotype:" + JavaAnnotationProfileBuilder.PROFILE_NAME + "#" + JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE);
        }

        for (String key : JavaAnnotationProfileBuilder.TOOL_TAG_KEYS) {
            if (key == null || key.isBlank()) continue;
            ensureStringAttribute(profile, st, key);
        }
    }

    /** Ensure a stereotype exists for an annotation. */
    Stereotype ensureStereotype(Profile profile, String simpleName, String qualifiedName) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(simpleName, "simpleName");

        String desiredName = sanitizeUmlName(simpleName);
        String qn = qualifiedName;

        // If stereotype already exists with matching qualifiedName, reuse.
        for (Stereotype st : profile.getOwnedStereotypes()) {
            if (desiredName.equals(st.getName())) {
                String existingQn = getQualifiedNameMeta(st);
                if (Objects.equals(existingQn, qn) || (existingQn == null && qn == null)) {
                    JavaAnnotationProfileBuilder.annotateIdIfMissing(st, stereotypeIdKey(qn, desiredName));
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
        JavaAnnotationProfileBuilder.annotateIdIfMissing(st, stereotypeIdKey(qn, finalName));
        stereotypeNameToQualified.put(finalName, qn);
        return st;
    }

    /** Ensure a String-typed attribute exists on the stereotype. */
    Property ensureStringAttribute(Profile profile, Stereotype st, String attrName) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(st, "st");
        Objects.requireNonNull(attrName, "attrName");

        String name = sanitizeUmlName(attrName);
        for (Property p : st.getOwnedAttributes()) {
            if (name.equals(p.getName())) {
                JavaAnnotationProfileBuilder.annotateIdIfMissing(p, "StereotypeAttr:" + st.getName() + "#" + name);
                return p;
            }
        }
        PrimitiveType stringType = ensureStringPrimitive(profile);
        Property p = st.createOwnedAttribute(name, stringType);
        JavaAnnotationProfileBuilder.annotateIdIfMissing(p, "StereotypeAttr:" + st.getName() + "#" + name);
        return p;
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

    private static PrimitiveType ensureStringPrimitive(Profile profile) {
        // Prefer an existing global "String" primitive anywhere in the enclosing UML Model.
        org.eclipse.uml2.uml.Package searchRoot = profile.getModel();
        if (searchRoot == null) {
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
                JavaAnnotationProfileBuilder.annotateIdIfMissing(existing, "Primitive:String");
                return existing;
            }
        }

        // As a last resort, create the primitive in the model-level "_primitives" package (NOT inside the profile).
        org.eclipse.uml2.uml.Package owner = searchRoot != null ? searchRoot : profile;
        org.eclipse.uml2.uml.Package primitivesPkg = findOrCreateChildPackage(owner, "_primitives");

        PrimitiveType pt = UMLFactory.eINSTANCE.createPrimitiveType();
        pt.setName("String");
        primitivesPkg.getPackagedElements().add(pt);
        JavaAnnotationProfileBuilder.annotateIdIfMissing(pt, "Primitive:String");
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
        JavaAnnotationProfileBuilder.annotateIdIfMissing(p, "Package:" + name);
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

    private static void setQualifiedNameMeta(Stereotype st, String qualifiedName) {
        EAnnotation ann = st.getEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
        if (ann == null) ann = st.createEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
        if (qualifiedName != null) {
            ann.getDetails().put("qualifiedName", qualifiedName);
        }
    }

    private static String getQualifiedNameMeta(Stereotype st) {
        EAnnotation ann = st.getEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
        if (ann == null) return null;
        return ann.getDetails().get("qualifiedName");
    }

    private static String sanitizeSuffixFromQualified(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) return "unknown";
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            sb.append(ok ? c : '_');
        }
        String out = sb.toString();
        if (!out.isEmpty() && Character.isDigit(out.charAt(0))) {
            out = "_" + out;
        }
        return out;
    }
}

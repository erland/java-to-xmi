package se.erland.javatoxmi.uml;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;

/**
 * Step 3 — Profile builder for Java annotations.
 *
 * MVP responsibility:
 * - Ensure a Profile exists in the UML Model
 * - Create/reuse stereotypes representing Java annotations
 * - Create/reuse stereotype attributes (tag definitions) as String
 * - Attach deterministic IDs and store qualified-name metadata
 *
 * NOTE: This step intentionally does not create metaclass Extensions nor apply the profile.
 * Those are handled in later steps.
 */
public final class JavaAnnotationProfileBuilder {

    public static final String PROFILE_NAME = "JavaAnnotations";
    public static final String JAVA_ANN_SOURCE = "java-to-xmi:java";

    // Tracks used stereotype names to avoid collisions.
    private final Map<String, String> stereotypeNameToQualified = new HashMap<>();

    /** Ensure the JavaAnnotations profile exists under the given UML model. */
    public Profile ensureProfile(Model model) {
        Objects.requireNonNull(model, "model");
        // Find existing
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && PROFILE_NAME.equals(p.getName())) {
                annotateIdIfMissing(p, "Profile:" + PROFILE_NAME);
                ensureStringPrimitive(p);
                indexExistingStereotypes(p);
                return p;
            }
        }
        Profile profile = UMLFactory.eINSTANCE.createProfile();
        profile.setName(PROFILE_NAME);
        model.getPackagedElements().add(profile);
        annotateIdIfMissing(profile, "Profile:" + PROFILE_NAME);
        ensureStringPrimitive(profile);
        indexExistingStereotypes(profile);
        return profile;
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
        for (org.eclipse.uml2.uml.Type t : profile.getOwnedTypes()) {
            if (t instanceof PrimitiveType pt && "String".equals(pt.getName())) {
                annotateIdIfMissing(pt, "Primitive:String");
                return pt;
            }
        }
        PrimitiveType pt = UMLFactory.eINSTANCE.createPrimitiveType();
        pt.setName("String");
        profile.getOwnedTypes().add(pt);
        annotateIdIfMissing(pt, "Primitive:String");
        return pt;
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
}

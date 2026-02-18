package se.erland.javatoxmi.xmi;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import se.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;
import se.erland.javatoxmi.uml.UmlIdStrategy;

/**
 * Ensures the serialized XMI has the required namespace declaration and a
 * <profileApplication> referencing the in-model JavaAnnotations profile.
 */
final class ProfileApplicationInjector {

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";

    private ProfileApplicationInjector() {}

    static Profile findJavaAnnotationsProfile(Model model) {
        if (model == null) return null;
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && JavaAnnotationProfileBuilder.PROFILE_NAME.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    static String ensureProfileNamespaceDeclared(String xml) {
        if (xml == null) return null;
        // We use the profile name as prefix, e.g. xmlns:JavaAnnotations="..."
        String decl = "xmlns:" + JavaAnnotationProfileBuilder.PROFILE_NAME + "=\"" + StereotypeXmiInjector.PROFILE_ECORE_NS + "\"";
        if (xml.contains(decl)) return xml;

        int rootStart = xml.indexOf("<xmi:XMI");
        if (rootStart >= 0) {
            int gt = xml.indexOf('>', rootStart);
            if (gt > rootStart) {
                String before = xml.substring(0, gt);
                String after = xml.substring(gt);
                return before + " " + decl + after;
            }
        }

        // Fallback: add to uml:Model if file doesn't have an xmi:XMI wrapper.
        rootStart = xml.indexOf("<uml:Model");
        if (rootStart >= 0) {
            int gt = xml.indexOf('>', rootStart);
            if (gt > rootStart) {
                String before = xml.substring(0, gt);
                String after = xml.substring(gt);
                return before + " " + decl + after;
            }
        }
        return xml;
    }

    static String ensureProfileApplicationPresent(String xml, Profile profile) {
        if (xml == null) return null;
        if (profile == null) return xml;

        // If a profileApplication already exists, leave as-is.
        if (xml.contains("<profileApplication") && xml.contains("<appliedProfile")) return xml;

        int modelStart = xml.indexOf("<uml:Model");
        if (modelStart < 0) return xml;
        int modelGt = xml.indexOf('>', modelStart);
        if (modelGt < 0) return xml;

        String profileId = "_" + getAnnotatedIdOrDefault(profile, UmlIdStrategy.id("Profile:" + JavaAnnotationProfileBuilder.PROFILE_NAME));
        String appId = "_" + UmlIdStrategy.id("ProfileApplication:" + profileId);
        String pa = "\n  <profileApplication xmi:id=\"" + XmiDomUtil.escapeAttr(appId) + "\">\n" +
                "    <appliedProfile href=\"#" + XmiDomUtil.escapeAttr(profileId) + "\"/>\n" +
                "  </profileApplication>\n";

        return xml.substring(0, modelGt + 1) + pa + xml.substring(modelGt + 1);
    }

    private static String getAnnotatedIdOrDefault(org.eclipse.uml2.uml.Element el, String fallback) {
        if (el == null) return fallback;
        EAnnotation ann = el.getEAnnotation(ID_ANN_SOURCE);
        if (ann != null) {
            String v = ann.getDetails().get(ID_ANN_KEY);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return fallback;
    }
}

package info.isaksson.erland.javatoxmi.xmi;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import info.isaksson.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;
import info.isaksson.erland.javatoxmi.uml.UmlIdStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ensures the serialized XMI has required namespace declarations and <profileApplication>
 * entries for in-model UML Profiles.
 *
 * <p>Historically this injector assumed a single hard-coded profile (JavaAnnotations).
 * It now supports multiple profiles, including profiles materialized from IR stereotype
 * definitions (v2 IR format).</p>
 */
final class ProfileApplicationInjector {

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";

    private ProfileApplicationInjector() {}

    static List<Profile> findAllProfiles(Model model) {
        List<Profile> out = new ArrayList<>();
        if (model == null) return out;
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p) out.add(p);
        }
        out.sort(Comparator.comparing(p -> p == null || p.getName() == null ? "" : p.getName()));
        return out;
    }

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
        // Backwards-compatible helper: declare JavaAnnotations namespace.
        return ensureProfileNamespaceDeclared(xml, JavaAnnotationProfileBuilder.PROFILE_NAME, JavaAnnotationProfileBuilder.PROFILE_URI);
    }

    static String ensureProfileNamespaceDeclared(String xml, String prefix, String uri) {
        if (xml == null) return null;
        if (prefix == null || prefix.isBlank()) return xml;
        if (uri == null || uri.isBlank()) return xml;

        String decl = "xmlns:" + prefix + "=\"" + uri + "\"";
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

        String profileName = profile.getName() == null ? "" : profile.getName();
        if (profileName.isBlank()) return xml;

        // If a profileApplication for THIS profile already exists, leave as-is.
        String profileId = "_" + getAnnotatedIdOrDefault(profile, UmlIdStrategy.id("Profile:" + profileName));
        if (xml.contains("href=\"#"+ XmiDomUtil.escapeAttr(profileId) + "\"")) {
            return xml;
        }

        int modelStart = xml.indexOf("<uml:Model");
        if (modelStart < 0) return xml;
        int modelGt = xml.indexOf('>', modelStart);
        if (modelGt < 0) return xml;

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

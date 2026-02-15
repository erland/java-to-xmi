package se.erland.javatoxmi.xmi;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;
import se.erland.javatoxmi.uml.UmlIdStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Step 5 (recommended approach) — Apply stereotypes by injecting deterministic XMI extensions.
 *
 * Why this exists:
 * Eclipse UML2's runtime APIs for Profile#define / applyProfile / applyStereotype are brittle
 * across versions and require strict metamodel identity. Instead of depending on those APIs,
 * we emit an XMI extension that records stereotype applications in a stable, tool-agnostic
 * format that is easy for our own importer (and other tooling, if desired).
 */
final class StereotypeXmiInjector {

    static final String J2X_NS = "https://se.erland/javatoxmi/xmi/1.0";
    static final String EXTENDER = "java-to-xmi";

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";

    private StereotypeXmiInjector() {}

    static String inject(Model umlModel, JModel jModel, String xmiWrappedXml) {
        if (umlModel == null || jModel == null) return xmiWrappedXml;

        Profile profile = findJavaAnnotationsProfile(umlModel);
        if (profile == null) return xmiWrappedXml;

        Map<String, String> stereotypeIdByQualifiedName = indexStereotypesByQualifiedName(profile);

        // Determinism: sort types + annotations + tags for stable injected output.
        List<JType> types = new ArrayList<>(jModel.types);
        types.sort((a, b) -> {
            String aq = a == null ? "" : (a.qualifiedName == null ? "" : a.qualifiedName);
            String bq = b == null ? "" : (b.qualifiedName == null ? "" : b.qualifiedName);
            return aq.compareTo(bq);
        });

        List<InjectedApplication> apps = new ArrayList<>();
        for (JType t : types) {
            if (t.annotations == null || t.annotations.isEmpty()) continue;

            String baseId = "_" + UmlIdStrategy.id("Classifier:" + t.qualifiedName);

            List<JAnnotationUse> anns = new ArrayList<>(t.annotations);
            anns.sort((a, b) -> {
                String aq = a == null ? "" : (a.qualifiedName != null ? a.qualifiedName : (a.simpleName != null ? a.simpleName : ""));
                String bq = b == null ? "" : (b.qualifiedName != null ? b.qualifiedName : (b.simpleName != null ? b.simpleName : ""));
                return aq.compareTo(bq);
            });

            for (JAnnotationUse ann : anns) {
                if (ann == null) continue;
                String qn = ann.qualifiedName;
                if (qn == null) qn = "";

                String stereotypeId = stereotypeIdByQualifiedName.get(qn);
                if (stereotypeId == null) {
                    // Best effort: fall back to matching by simple name when qualification is missing.
                    stereotypeId = stereotypeIdByQualifiedName.get("#" + (ann.simpleName == null ? "" : ann.simpleName));
                }
                if (stereotypeId == null) continue;

                String appId = "_" + UmlIdStrategy.id("StereotypeApplication:" + t.qualifiedName + "@" + (ann.qualifiedName != null ? ann.qualifiedName : ann.simpleName));

                InjectedApplication ia = new InjectedApplication();
                ia.xmiId = appId;
                ia.baseId = baseId;
                ia.stereotypeId = stereotypeId;
                ia.stereotypeQualifiedName = ann.qualifiedName;
                if (ann.values != null) {
                    ia.tags.putAll(ann.values);
                }
                apps.add(ia);
            }
        }

        if (apps.isEmpty()) return xmiWrappedXml;

        String profileId = "_" + getAnnotatedIdOrDefault(profile, UmlIdStrategy.id("Profile:" + JavaAnnotationProfileBuilder.PROFILE_NAME));

        apps.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeQualifiedName).compareTo(nullSafe(b.stereotypeQualifiedName));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        String extensionXml = buildExtension(profileId, apps);

        return injectBeforeClosingXmi(xmiWrappedXml, extensionXml);
    }

    private static Profile findJavaAnnotationsProfile(Model model) {
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && JavaAnnotationProfileBuilder.PROFILE_NAME.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    private static Map<String, String> indexStereotypesByQualifiedName(Profile profile) {
        Map<String, String> out = new TreeMap<>();
        for (Stereotype st : profile.getOwnedStereotypes()) {
            String id = "_" + getAnnotatedIdOrDefault(st, null);
            if (id == null) continue;

            // Primary key: qualified name from our meta annotation
            EAnnotation javaMeta = st.getEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
            if (javaMeta != null) {
                String qn = javaMeta.getDetails().get("qualifiedName");
                if (qn != null) {
                    out.put(qn, id);
                }
            }
            // Secondary key: marker for unqualified + simple name
            if (st.getName() != null) {
                out.put("#" + st.getName(), id);
            }
        }
        return out;
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

    private static String buildExtension(String profileId, List<InjectedApplication> apps) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <xmi:Extension extender=\"").append(EXTENDER).append("\">\n");
        sb.append("    <j2x:profileApplication profile=\"").append(escapeAttr(profileId)).append("\"/>\n");
        sb.append("    <j2x:stereotypeApplications>\n");
        for (InjectedApplication a : apps) {
            sb.append("      <j2x:apply xmi:id=\"").append(escapeAttr(a.xmiId)).append("\"");
            sb.append(" base=\"").append(escapeAttr(a.baseId)).append("\"");
            sb.append(" stereotype=\"").append(escapeAttr(a.stereotypeId)).append("\"");
            if (a.stereotypeQualifiedName != null && !a.stereotypeQualifiedName.isBlank()) {
                sb.append(" stereotypeQualifiedName=\"").append(escapeAttr(a.stereotypeQualifiedName)).append("\"");
            }
            sb.append(">\n");

            // Determinism: tags in key order.
            Map<String, String> tags = new TreeMap<>(a.tags);
            for (Map.Entry<String, String> e : tags.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isBlank()) continue;
                String v = e.getValue();
                if (v == null) v = "";
                sb.append("        <j2x:tag name=\"").append(escapeAttr(k)).append("\" value=\"").append(escapeAttr(v)).append("\"/>\n");
            }
            sb.append("      </j2x:apply>\n");
        }
        sb.append("    </j2x:stereotypeApplications>\n");
        sb.append("  </xmi:Extension>\n");
        return sb.toString();
    }

    private static String injectBeforeClosingXmi(String xml, String extension) {
        int closeIdx = xml.lastIndexOf("</xmi:XMI>");
        if (closeIdx < 0) return xml;
        return xml.substring(0, closeIdx) + extension + xml.substring(closeIdx);
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    private static final class InjectedApplication {
        String xmiId;
        String baseId;
        String stereotypeId;
        String stereotypeQualifiedName;
        final Map<String, String> tags = new TreeMap<>();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}

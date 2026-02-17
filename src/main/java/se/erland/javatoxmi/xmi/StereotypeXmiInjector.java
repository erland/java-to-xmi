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
 * Step 5 (recommended approach) — Apply stereotypes by injecting deterministic, UML2-style
 * stereotype applications into the serialized XMI.
 *
 * Why this exists:
 * Eclipse UML2's runtime APIs for Profile#define / applyProfile / applyStereotype are brittle
 * across versions and require strict metamodel identity. Instead of depending on those APIs,
 * we post-process the saved XMI and inject:
 *   1) a <profileApplication> under the root uml:Model
 *   2) stereotype application instances as <JavaAnnotations:Stereo ... base_Class="..."/>
 *
 * This is closer to what many UML tools expect than custom xmi:Extension payloads, while
 * remaining deterministic and independent of UML2 runtime behavior.
 */
final class StereotypeXmiInjector {

    /** Namespace URI for the generated EPackage that represents the profile in XMI. */
    static final String PROFILE_ECORE_NS = JavaAnnotationProfileBuilder.PROFILE_URI;

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";

    private StereotypeXmiInjector() {}

    static String inject(Model umlModel, JModel jModel, String xmiWrappedXml) {
        if (umlModel == null || jModel == null) return xmiWrappedXml;

        Profile profile = findJavaAnnotationsProfile(umlModel);
        if (profile == null) return xmiWrappedXml;

        Map<String, StereotypeInfo> stereotypeByQualifiedName = indexStereotypesByQualifiedName(profile);

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

                StereotypeInfo stInfo = stereotypeByQualifiedName.get(qn);
                if (stInfo == null) {
                    // Best effort: fall back to matching by simple name when qualification is missing.
                    stInfo = stereotypeByQualifiedName.get("#" + (ann.simpleName == null ? "" : ann.simpleName));
                }
                if (stInfo == null) continue;

                String appId = "_" + UmlIdStrategy.id("StereotypeApplication:" + t.qualifiedName + "@" + (ann.qualifiedName != null ? ann.qualifiedName : ann.simpleName));

                InjectedApplication ia = new InjectedApplication();
                ia.xmiId = appId;
                ia.baseId = baseId;
                ia.stereotypeId = stInfo.id;
                ia.stereotypeName = stInfo.name;
                ia.baseProperty = basePropertyForType(t);
                ia.stereotypeQualifiedName = ann.qualifiedName;
                if (ann.values != null) {
                    ia.tags.putAll(ann.values);
                }
                apps.add(ia);
            }
        }

        if (apps.isEmpty()) return xmiWrappedXml;

        // Ensure the profile Ecore namespace is declared for stereotype application element names.
        xmiWrappedXml = ensureProfileNamespaceDeclared(xmiWrappedXml);

        // Ensure the model has a profileApplication referencing the JavaAnnotations profile.
        String profileId = "_" + getAnnotatedIdOrDefault(profile, UmlIdStrategy.id("Profile:" + JavaAnnotationProfileBuilder.PROFILE_NAME));
        xmiWrappedXml = ensureProfileApplicationPresent(xmiWrappedXml, profileId);

        apps.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeQualifiedName).compareTo(nullSafe(b.stereotypeQualifiedName));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        String appsXml = buildStereotypeApplications(apps);
        return injectBeforeClosingXmi(xmiWrappedXml, appsXml);
    }

    private static String ensureProfileNamespaceDeclared(String xml) {
        if (xml == null) return null;
        // We use the profile name as prefix, e.g. xmlns:JavaAnnotations="..."
        String decl = "xmlns:" + JavaAnnotationProfileBuilder.PROFILE_NAME + "=\"" + PROFILE_ECORE_NS + "\"";
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

    private static String ensureProfileApplicationPresent(String xml, String profileId) {
        if (xml == null) return null;
        // If a profileApplication already exists, leave as-is.
        if (xml.contains("<profileApplication") && xml.contains("<appliedProfile")) return xml;

        int modelStart = xml.indexOf("<uml:Model");
        if (modelStart < 0) return xml;
        int modelGt = xml.indexOf('>', modelStart);
        if (modelGt < 0) return xml;

        String appId = "_" + UmlIdStrategy.id("ProfileApplication:" + profileId);
        String pa = "\n  <profileApplication xmi:id=\"" + escapeAttr(appId) + "\">\n" +
                "    <appliedProfile href=\"#" + escapeAttr(profileId) + "\"/>\n" +
                "  </profileApplication>\n";

        return xml.substring(0, modelGt + 1) + pa + xml.substring(modelGt + 1);
    }

    private static Profile findJavaAnnotationsProfile(Model model) {
        for (org.eclipse.uml2.uml.PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && JavaAnnotationProfileBuilder.PROFILE_NAME.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    private static Map<String, StereotypeInfo> indexStereotypesByQualifiedName(Profile profile) {
        Map<String, StereotypeInfo> out = new TreeMap<>();
        for (Stereotype st : profile.getOwnedStereotypes()) {
            String id = "_" + getAnnotatedIdOrDefault(st, null);
            if (id == null) continue;
            String name = st.getName() == null ? "" : st.getName();
            StereotypeInfo info = new StereotypeInfo(id, name);

            // Primary key: qualified name from our meta annotation
            EAnnotation javaMeta = st.getEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
            if (javaMeta != null) {
                String qn = javaMeta.getDetails().get("qualifiedName");
                if (qn != null) {
                    out.put(qn, info);
                }
            }
            // Secondary key: marker for unqualified + simple name
            if (st.getName() != null) {
                out.put("#" + st.getName(), info);
            }
        }
        return out;
    }

    private static String basePropertyForType(JType t) {
        // Map to UML metaclass extension property names.
        if (t == null || t.kind == null) return "base_Class";
        switch (t.kind) {
            case INTERFACE:
                return "base_Interface";
            case ENUM:
                return "base_Enumeration";
            default:
                return "base_Class";
        }
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

    private static String buildStereotypeApplications(List<InjectedApplication> apps) {
        StringBuilder sb = new StringBuilder();
        for (InjectedApplication a : apps) {
            // UML2-style stereotype application element.
            sb.append("  <").append(JavaAnnotationProfileBuilder.PROFILE_NAME).append(":").append(escapeAttr(a.stereotypeName)).append(" xmi:id=\"")
                    .append(escapeAttr(a.xmiId)).append("\"");

            sb.append(" ").append(escapeAttr(a.baseProperty)).append("=\"").append(escapeAttr(a.baseId)).append("\"");

            // Determinism: tags emitted as attributes in key order.
            Map<String, String> tags = new TreeMap<>(a.tags);
            for (Map.Entry<String, String> e : tags.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isBlank()) continue;
                String v = e.getValue();
                if (v == null) v = "";
                sb.append(" ").append(escapeAttr(k)).append("=\"").append(escapeAttr(v)).append("\"");
            }
            sb.append("/>\n");
        }
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
        String stereotypeName;
        String baseProperty;
        String stereotypeQualifiedName;
        final Map<String, String> tags = new TreeMap<>();
    }

    private static final class StereotypeInfo {
        final String id;
        final String name;
        StereotypeInfo(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}

package info.isaksson.erland.javatoxmi.xmi;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;
import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;
import info.isaksson.erland.javatoxmi.uml.UmlIdStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds deterministic UML2-style stereotype application XML fragments for:
 * <ul>
 *   <li>Java annotation stereotypes applied to classifiers</li>
 *   <li>Tool tag stereotypes applied to any UML element carrying java-to-xmi:tags</li>
 * </ul>
 */
final class StereotypeApplicationInjector {

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";
    private static final String TAGS_ANN_SOURCE = "java-to-xmi:tags";
    private static final String RUNTIME_ANN_SOURCE = "java-to-xmi:runtime";
    private static final String RUNTIME_ANN_KEY = "stereotype";

    private StereotypeApplicationInjector() {}

    static String buildApplicationsXml(Model umlModel, JModel jModel, Profile profile) {
        if (umlModel == null || jModel == null || profile == null) return "";

        Map<String, StereotypeInfo> stereotypeByQualifiedName = indexStereotypesByQualifiedName(profile);

        // Determinism: sort types + annotations + tags for stable injected output.
        List<JType> types = new ArrayList<>(jModel.types);
        types.sort((a, b) -> {
            String aq = a == null ? "" : (a.qualifiedName == null ? "" : a.qualifiedName);
            String bq = b == null ? "" : (b.qualifiedName == null ? "" : b.qualifiedName);
            return aq.compareTo(bq);
        });

        List<InjectedApplication> apps = new ArrayList<>();

        // 1) Java annotation stereotype applications (types)
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

        // 2) Tool tag stereotype applications (any UML Element with java-to-xmi:tags annotation)
        StereotypeInfo toolTags = stereotypeByQualifiedName.get("#" + JavaAnnotationProfileBuilder.TOOL_TAGS_STEREOTYPE);
        if (toolTags != null) {
            apps.addAll(collectToolTagApplications(umlModel, toolTags));
        }

        // 3) Runtime stereotype applications (any UML Element with java-to-xmi:runtime stereotype marker)
        apps.addAll(collectRuntimeStereotypeApplications(umlModel, stereotypeByQualifiedName));

        // (apps may now include runtime relations too)

        if (apps.isEmpty()) return "";

        apps.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeQualifiedName).compareTo(nullSafe(b.stereotypeQualifiedName));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        return buildStereotypeApplications(apps);
    }

    private static List<InjectedApplication> collectToolTagApplications(Model umlModel, StereotypeInfo toolTags) {
        List<InjectedApplication> out = new ArrayList<>();
        if (umlModel == null || toolTags == null) return out;

        // Traverse all UML elements deterministically via EMF containment.
        List<Element> elements = new ArrayList<>();
        elements.add(umlModel);
        umlModel.eAllContents().forEachRemaining(eo -> {
            if (eo instanceof Element el) elements.add(el);
        });

        for (Element el : elements) {
            if (el == null) continue;
            EAnnotation ann = el.getEAnnotation(TAGS_ANN_SOURCE);
            if (ann == null || ann.getDetails() == null || ann.getDetails().isEmpty()) continue;

            String baseRaw = getAnnotatedIdOrDefault(el, null);
            if (baseRaw == null || baseRaw.isBlank()) {
                // If we cannot address the base element reliably, skip.
                continue;
            }
            String baseId = baseRaw.startsWith("_") ? baseRaw : "_" + baseRaw;

            InjectedApplication ia = new InjectedApplication();
            ia.baseId = baseId;
            ia.baseProperty = "base_NamedElement";
            ia.stereotypeId = toolTags.id;
            ia.stereotypeName = toolTags.name;

            // Deterministic application id
            ia.xmiId = "_" + UmlIdStrategy.id("StereotypeApplication:" + toolTags.name + "@" + baseRaw);

            // Copy only keys that exist as stereotype attributes to avoid unknown-attribute issues in some tools.
            for (String k : JavaAnnotationProfileBuilder.TOOL_TAG_KEYS) {
                if (k == null || k.isBlank()) continue;
                String v = ann.getDetails().get(k);
                if (v != null) ia.tags.put(k, v);
            }
            out.add(ia);
        }

        // Sort for stable injection
        out.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        return out;
    }
    private static List<InjectedApplication> collectRuntimeStereotypeApplications(Model umlModel, Map<String, StereotypeInfo> stereotypeByQualifiedName) {
        List<InjectedApplication> out = new ArrayList<>();
        if (umlModel == null || stereotypeByQualifiedName == null || stereotypeByQualifiedName.isEmpty()) return out;

        // Traverse all UML elements deterministically via EMF containment.
        List<Element> elements = new ArrayList<>();
        elements.add(umlModel);
        umlModel.eAllContents().forEachRemaining(eo -> {
            if (eo instanceof Element el) elements.add(el);
        });

        for (Element el : elements) {
            if (el == null) continue;
            EAnnotation ann = el.getEAnnotation(RUNTIME_ANN_SOURCE);
            if (ann == null || ann.getDetails() == null) continue;

            String stName = ann.getDetails().get(RUNTIME_ANN_KEY);
            if (stName == null || stName.isBlank()) continue;

            // Support multiple runtime stereotypes, comma-separated, deterministically.
            String[] stNames = stName.split(",");
            for (String rawName : stNames) {
                String one = rawName == null ? "" : rawName.trim();
                if (one.isBlank()) continue;

                StereotypeInfo st = stereotypeByQualifiedName.get("#" + one);
                if (st == null) continue; // unknown stereotype, skip

            String baseRaw = getAnnotatedIdOrDefault(el, null);
                if (baseRaw == null || baseRaw.isBlank()) continue;
                String baseId = baseRaw.startsWith("_") ? baseRaw : "_" + baseRaw;

                InjectedApplication ia = new InjectedApplication();
                ia.baseId = baseId;
                ia.baseProperty = basePropertyForElement(el);
                ia.stereotypeId = st.id;
                ia.stereotypeName = st.name;

                ia.xmiId = "_" + UmlIdStrategy.id("StereotypeApplication:" + st.name + "@" + baseRaw);

                // Runtime stereotypes currently carry no typed attributes; tags remain in ToolTags.
                out.add(ia);
            }
        }

        out.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        return out;
    }

    private static String basePropertyForElement(Element el) {
        if (el == null) return "base_NamedElement";
        if (el instanceof org.eclipse.uml2.uml.Dependency) return "base_Dependency";
        if (el instanceof org.eclipse.uml2.uml.Operation) return "base_Operation";
        if (el instanceof org.eclipse.uml2.uml.Class) return "base_Class";
        if (el instanceof org.eclipse.uml2.uml.Interface) return "base_Interface";
        if (el instanceof org.eclipse.uml2.uml.Package) return "base_Package";
        if (el instanceof org.eclipse.uml2.uml.Artifact) return "base_Artifact";
        return "base_NamedElement";
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
            sb.append("  <").append(JavaAnnotationProfileBuilder.PROFILE_NAME).append(":").append(XmiDomUtil.escapeAttr(a.stereotypeName)).append(" xmi:id=\"")
                    .append(XmiDomUtil.escapeAttr(a.xmiId)).append("\"");

            sb.append(" ").append(XmiDomUtil.escapeAttr(a.baseProperty)).append("=\"").append(XmiDomUtil.escapeAttr(a.baseId)).append("\"");

            // Determinism: tags emitted as attributes in key order.
            Map<String, String> tags = new TreeMap<>(a.tags);
            for (Map.Entry<String, String> e : tags.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isBlank()) continue;
                String v = e.getValue();
                if (v == null) v = "";
                sb.append(" ").append(XmiDomUtil.escapeAttr(k)).append("=\"").append(XmiDomUtil.escapeAttr(v)).append("\"");
            }
            sb.append("/>\n");
        }
        return sb.toString();
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

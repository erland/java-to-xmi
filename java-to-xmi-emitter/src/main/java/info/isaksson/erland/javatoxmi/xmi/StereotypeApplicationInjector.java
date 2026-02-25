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
 *   <li>Runtime stereotypes applied to any UML element carrying java-to-xmi:runtime markers</li>
 * </ul>
 *
 * <p>Originally this injector assumed a single profile (JavaAnnotations) and used a fixed XML prefix.
 * It now supports multiple profiles: the XML prefix for a stereotype application is the owning profile name
 * (e.g. &lt;Angular:AngularComponent .../&gt;).</p>
 */
final class StereotypeApplicationInjector {

    private static final String ID_ANN_SOURCE = "java-to-xmi:id";
    private static final String ID_ANN_KEY = "id";
    private static final String TAGS_ANN_SOURCE = "java-to-xmi:tags";
    private static final String RUNTIME_ANN_SOURCE = "java-to-xmi:runtime";
    private static final String RUNTIME_ANN_KEY = "stereotype";

    private StereotypeApplicationInjector() {}

    static String buildApplicationsXml(Model umlModel, JModel jModel, List<Profile> profiles) {
        if (umlModel == null || jModel == null || profiles == null || profiles.isEmpty()) return "";

        Map<String, StereotypeInfo> stereotypeByQualifiedName = indexStereotypesByQualifiedName(profiles);
        List<InjectedApplication> apps = new ArrayList<>();

        // 1) Java annotation stereotype applications (types)
        List<JType> types = new ArrayList<>(jModel.types);
        types.sort((a, b) -> {
            String aq = a == null ? "" : (a.qualifiedName != null ? a.qualifiedName : "");
            String bq = b == null ? "" : (b.qualifiedName != null ? b.qualifiedName : "");
            return aq.compareTo(bq);
        });

        for (JType t : types) {
            if (t == null) continue;
            if (t.annotations == null || t.annotations.isEmpty()) continue;

            // Base element id must match the xmi:id of the classifier created in UML builder.
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
                    stInfo = stereotypeByQualifiedName.get("#" + (ann.simpleName == null ? "" : ann.simpleName));
                }
                if (stInfo == null) continue;

                String appId = "_" + UmlIdStrategy.id("StereotypeApplication:" + stInfo.profileName + ":" + stInfo.name + "@" + baseId);

                InjectedApplication ia = new InjectedApplication();
                ia.profilePrefix = stInfo.profileName;
                ia.xmiId = appId;
                ia.baseId = baseId;
                ia.stereotypeId = stInfo.id;
                ia.stereotypeName = stInfo.name;
                ia.baseProperty = basePropertyForType(t);
                ia.stereotypeQualifiedName = qn;
                ia.typeQualifiedName = ann.qualifiedName;

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

        if (apps.isEmpty()) return "";

        apps.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.profilePrefix).compareTo(nullSafe(b.profilePrefix));
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
            if (baseRaw == null || baseRaw.isBlank()) continue;

            String baseId = baseRaw.startsWith("_") ? baseRaw : "_" + baseRaw;

            InjectedApplication ia = new InjectedApplication();
            ia.profilePrefix = toolTags.profileName;
            ia.baseId = baseId;
            ia.baseProperty = "base_NamedElement";
            ia.stereotypeId = toolTags.id;
            ia.stereotypeName = toolTags.name;

            ia.xmiId = "_" + UmlIdStrategy.id("StereotypeApplication:" + toolTags.profileName + ":" + toolTags.name + "@" + baseRaw);

            // Copy only keys that exist as stereotype attributes to avoid unknown-attribute issues in some tools.
            for (String k : JavaAnnotationProfileBuilder.TOOL_TAG_KEYS) {
                if (k == null || k.isBlank()) continue;
                String v = ann.getDetails().get(k);
                if (v != null) ia.tags.put(k, v);
            }
            out.add(ia);
        }

        out.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.profilePrefix).compareTo(nullSafe(b.profilePrefix));
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

        List<Element> elements = new ArrayList<>();
        elements.add(umlModel);
        umlModel.eAllContents().forEachRemaining(eo -> {
            if (eo instanceof Element el) elements.add(el);
        });

        // Deterministic ordering
        elements.sort((a, b) -> nullSafe(getAnnotatedIdOrDefault(a, "")).compareTo(nullSafe(getAnnotatedIdOrDefault(b, ""))));

        for (Element el : elements) {
            if (el == null) continue;

            EAnnotation rt = el.getEAnnotation(RUNTIME_ANN_SOURCE);
            if (rt == null) continue;

            String raw = rt.getDetails().get(RUNTIME_ANN_KEY);
            if (raw == null || raw.isBlank()) continue;

            String[] stNames = raw.split(",");
            for (String rawName : stNames) {
                String one = rawName == null ? "" : rawName.trim();
                if (one.isBlank()) continue;

                StereotypeInfo st = stereotypeByQualifiedName.get("#" + one);
                if (st == null) continue;

                String baseRaw = getAnnotatedIdOrDefault(el, null);
                if (baseRaw == null || baseRaw.isBlank()) continue;

                String baseId = baseRaw.startsWith("_") ? baseRaw : "_" + baseRaw;

                InjectedApplication ia = new InjectedApplication();
                ia.profilePrefix = st.profileName;
                ia.baseId = baseId;
                ia.baseProperty = basePropertyForElement(el);
                ia.stereotypeId = st.id;
                ia.stereotypeName = st.name;

                ia.xmiId = "_" + UmlIdStrategy.id("StereotypeApplication:" + st.profileName + ":" + st.name + "@" + baseRaw);

                out.add(ia);
            }
        }

        out.sort((a, b) -> {
            int c = nullSafe(a.baseId).compareTo(nullSafe(b.baseId));
            if (c != 0) return c;
            c = nullSafe(a.profilePrefix).compareTo(nullSafe(b.profilePrefix));
            if (c != 0) return c;
            c = nullSafe(a.stereotypeId).compareTo(nullSafe(b.stereotypeId));
            if (c != 0) return c;
            return nullSafe(a.xmiId).compareTo(nullSafe(b.xmiId));
        });

        return out;
    }

    private static String basePropertyForType(JType t) {
        // Map to UML metaclass extension property names (keep original behavior).
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

    private static String basePropertyForElement(Element el) {
        // Conservative mapping based on metaclass name.
        String cn = el == null ? "" : el.eClass().getName();
        if ("Class".equals(cn)) return "base_Class";
        if ("Interface".equals(cn)) return "base_Interface";
        if ("Enumeration".equals(cn)) return "base_Enumeration";
        return "base_NamedElement";
    }

    private static Map<String, StereotypeInfo> indexStereotypesByQualifiedName(List<Profile> profiles) {
        Map<String, StereotypeInfo> out = new TreeMap<>();
        if (profiles == null) return out;

        for (Profile profile : profiles) {
            if (profile == null) continue;
            String profileName = profile.getName() == null ? "" : profile.getName();
            if (profileName.isBlank()) continue;

            for (Stereotype st : profile.getOwnedStereotypes()) {
                String id = "_" + getAnnotatedIdOrDefault(st, null);
                if (id == null) continue;
                String name = st.getName() == null ? "" : st.getName();
                StereotypeInfo info = new StereotypeInfo(id, name, profileName);

                // Primary key: qualified name from our meta annotation
                EAnnotation javaMeta = st.getEAnnotation(JavaAnnotationProfileBuilder.JAVA_ANN_SOURCE);
                if (javaMeta != null) {
                    String qn = javaMeta.getDetails().get("qualifiedName");
                    if (qn != null && !qn.isBlank()) {
                        out.put(qn, info);
                    }
                }
                if (st.getName() != null && !st.getName().isBlank()) {
                    out.put("#" + st.getName(), info);
                }
            }
        }
        return out;
    }

    private static String buildStereotypeApplications(List<InjectedApplication> apps) {
        StringBuilder sb = new StringBuilder();
        for (InjectedApplication a : apps) {
            String prefix = (a.profilePrefix == null || a.profilePrefix.isBlank())
                    ? JavaAnnotationProfileBuilder.PROFILE_NAME
                    : a.profilePrefix;

            sb.append("  <").append(XmiDomUtil.escapeAttr(prefix)).append(":").append(XmiDomUtil.escapeAttr(a.stereotypeName)).append(" xmi:id=\"")
                    .append(XmiDomUtil.escapeAttr(a.xmiId)).append("\"");

            sb.append(" ").append(XmiDomUtil.escapeAttr(a.baseProperty)).append("=\"").append(XmiDomUtil.escapeAttr(a.baseId)).append("\"");

            // Tag attributes (only for stereotypes that define them; tool tags / java annotations)
            if (a.tags != null && !a.tags.isEmpty()) {
                for (Map.Entry<String, String> e : a.tags.entrySet()) {
                    String k = e.getKey();
                    if (k == null || k.isBlank()) continue;
                    String v = e.getValue();
                    if (v == null) continue;
                    sb.append(" ").append(XmiDomUtil.escapeAttr(k)).append("=\"").append(XmiDomUtil.escapeAttr(v)).append("\"");
                }
            }

            sb.append("/>\n");
        }
        return sb.toString();
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

    private static final class InjectedApplication {
        String profilePrefix;
        String xmiId;
        String baseId;
        String stereotypeId;
        String stereotypeName;
        String baseProperty;
        String stereotypeQualifiedName;
        String typeQualifiedName;
        Map<String, String> tags = new TreeMap<>();
    }

    private static final class StereotypeInfo {
        final String id;
        final String name;
        final String profileName;
        StereotypeInfo(String id, String name, String profileName) {
            this.id = id;
            this.name = name;
            this.profileName = profileName;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}

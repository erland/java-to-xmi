package se.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.VisibilityKind;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Small shared helpers used by the UML build steps.
 */
final class UmlBuilderSupport {
    private UmlBuilderSupport() {
    }

    static void setVisibility(NamedElement el, JVisibility v) {
        if (v == null) {
            el.setVisibility(VisibilityKind.PACKAGE_LITERAL);
            return;
        }
        switch (v) {
            case PUBLIC -> el.setVisibility(VisibilityKind.PUBLIC_LITERAL);
            case PROTECTED -> el.setVisibility(VisibilityKind.PROTECTED_LITERAL);
            case PRIVATE -> el.setVisibility(VisibilityKind.PRIVATE_LITERAL);
            case PACKAGE_PRIVATE -> el.setVisibility(VisibilityKind.PACKAGE_LITERAL);
        }
    }

    static String signatureKey(JMethod m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.name).append("(");
        boolean first = true;
        for (JParam p : m.params) {
            if (!first) sb.append(",");
            first = false;
            sb.append(p.type);
        }
        sb.append(")");
        return sb.toString();
    }

    static void annotateId(Element element, String key) {
        if (element == null) return;
        EAnnotation ann = element.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        if (ann == null) {
            ann = element.createEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        }
        ann.getDetails().put("id", key);
    }

    static void annotateTags(Element element, Map<String, String> tags) {
        if (element == null || tags == null || tags.isEmpty()) return;
        EAnnotation ann = element.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE);
        if (ann == null) {
            ann = element.createEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE);
        }
        // Deterministic key order.
        List<String> keys = new ArrayList<>(tags.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            String v = tags.get(k);
            if (v == null) continue;
            ann.getDetails().put(k, v);
        }
    }

    static void annotateJavaTypeIfGeneric(Element element, String javaTypeRef) {
        if (element == null || javaTypeRef == null) return;
        String s = javaTypeRef.trim();
        int lt = s.indexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt < 0 || gt < lt) return;

        // Preserve full Java generic type string.
        addAnnotationValue(element, "java-to-xmi:javaType", s);

        // Best-effort extraction of top-level args (no deep parsing).
        String inner = s.substring(lt + 1, gt).trim();
        if (inner.isEmpty()) return;

        List<String> args = splitTopLevelTypeArgs(inner);
        if (!args.isEmpty()) {
            addAnnotationValue(element, "java-to-xmi:typeArgs", String.join(", ", args));
        }
    }

    private static List<String> splitTopLevelTypeArgs(String inner) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);

            if (c == ',' && depth == 0) {
                String part = buf.toString().trim();
                if (!part.isEmpty()) out.add(part);
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        String last = buf.toString().trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    private static void addAnnotationValue(Element element, String source, String value) {
        if (element == null || source == null || value == null) return;
        EAnnotation ann = element.getEAnnotation(source);
        if (ann == null) {
            ann = element.createEAnnotation(source);
        }
        ann.getDetails().put("value", value);
    }
}

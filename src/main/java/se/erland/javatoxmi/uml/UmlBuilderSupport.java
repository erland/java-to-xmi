package se.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.ElementImport;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.VisibilityKind;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

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

    /**
     * Ensure every UML2 {@link Element} in the model has a deterministic java-to-xmi:id annotation.
     *
     * <p>This prevents the XMI writer from falling back to traversal-index-based IDs, which can
     * change when containment changes (e.g. when introducing nested types ownership).</p>
     */
    static void ensureAllElementsHaveId(Element root) {
        if (root == null) return;

        // Root first
        ensureId(root);

        TreeIterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (obj instanceof Element) {
                ensureId((Element) obj);
            }
        }
    }

    private static void ensureId(Element element) {
        if (element == null) return;
        EAnnotation ann = element.getEAnnotation(UmlBuilder.ID_ANNOTATION_SOURCE);
        if (ann != null) {
            String existing = ann.getDetails().get("id");
            if (existing != null && !existing.trim().isEmpty()) return;
        }

        // Prefer a name-based stable key when possible.
        String key;
        if (element instanceof NamedElement) {
            NamedElement ne = (NamedElement) element;
            String qn = safeUmlQualifiedName(ne);
            if (qn != null && !qn.isBlank()) {
                key = "Auto:" + ne.eClass().getName() + ":" + qn;
            } else {
                key = "Auto:" + ne.eClass().getName() + ":" + stableContainerHash(ne);
            }
        } else {
            key = "Auto:" + element.eClass().getName() + ":" + stableContainerHash(element);
        }

        annotateId(element, key);
    }

    private static String safeUmlQualifiedName(NamedElement ne) {
        // In UML2 3.x, getQualifiedName() can return null if not in a namespace chain.
        try {
            String qn = ne.getQualifiedName();
            if (qn != null && !qn.isBlank()) return qn;
        } catch (Exception ignore) {
        }

        // Build a conservative qualified name from namespace chain.
        List<String> parts = new ArrayList<>();
        NamedElement cur = ne;
        while (cur != null) {
            String n = cur.getName();
            if (n != null && !n.isBlank()) {
                parts.add(n);
            }
            EObject c = ((EObject) cur).eContainer();
            if (c instanceof NamedElement) cur = (NamedElement) c;
            else break;
        }
        if (parts.isEmpty()) return null;
        Collections.reverse(parts);
        return String.join("::", parts);
    }

    /**
     * Ensure an {@link ElementImport} exists in {@code pkg} for {@code imported}.
     *
     * <p>This is used for the optional "uml+import" nested types mode. It does not duplicate
     * the classifier in the package; it only adds an import so some consumers can discover
     * nested types more easily.</p>
     */
    static void ensureElementImport(Package pkg, PackageableElement imported) {
        if (pkg == null || imported == null) return;

        // Avoid duplicates
        try {
            for (ElementImport ei : pkg.getElementImports()) {
                if (ei != null && ei.getImportedElement() == imported) return;
            }
        } catch (Exception ignore) {
            // We'll fall through and attempt to create; duplicates are still unlikely.
        }

        ElementImport ei = UMLFactory.eINSTANCE.createElementImport();
        ei.setImportedElement(imported);
        ei.setVisibility(VisibilityKind.PUBLIC_LITERAL);

        // Attach via the real containment reference "elementImport" when present to avoid
        // derived/unmodifiable lists in older UML2 versions.
        if (pkg instanceof EObject) {
            EObject eo = (EObject) pkg;
            EStructuralFeature f = eo.eClass().getEStructuralFeature("elementImport");
            if (f != null) {
                Object cur = eo.eGet(f);
                if (cur instanceof EList) {
                    @SuppressWarnings("unchecked")
                    EList<EObject> list = (EList<EObject>) cur;
                    list.add((EObject) ei);
                    return;
                }
            }
        }

        // Fallback: try the typed list (may still work).
        pkg.getElementImports().add(ei);
    }

    private static String stableContainerHash(EObject obj) {
        // A deterministic fallback based on containment chain + eClass + (optional) name.
        // This is still sensitive to containment changes, but it is deterministic and avoids
        // dependence on traversal index ordering.
        StringBuilder sb = new StringBuilder();
        EObject cur = obj;
        while (cur != null) {
            sb.append('/').append(cur.eClass().getName());
            if (cur instanceof NamedElement) {
                String n = ((NamedElement) cur).getName();
                if (n != null && !n.isBlank()) sb.append(':').append(n.trim());
            }
            cur = cur.eContainer();
        }
        return shortSha256Hex(sb.toString());
    }

    private static String shortSha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : dig) {
                int v = b & 0xff;
                String hx = Integer.toHexString(v);
                if (hx.length() == 1) out.append('0');
                out.append(hx);
            }
            return out.substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((s == null ? "" : s).hashCode());
        }
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

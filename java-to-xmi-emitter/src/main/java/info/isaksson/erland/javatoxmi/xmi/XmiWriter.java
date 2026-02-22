package info.isaksson.erland.javatoxmi.xmi;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.UMLPackage;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Step 5 — XMI export (UML2/EMF serialization) + determinism hardening.
 *
 * Writes a UML2 {@link Model} to an .xmi file using EMF resource serialization.
 *
 * Determinism:
 * - We assign deterministic xmi:ids before saving, based on the EAnnotation created in Step 4.
 * - For elements that lack an annotation, we fall back to a stable hash of their containment path.
 */
public final class XmiWriter {

    private static final String ID_ANNOTATION_SOURCE = "java-to-xmi:id";
    private static final String ID_ANNOTATION_KEY = "id";

    private XmiWriter() {}

    public static void write(Model umlModel, Path outFile) throws IOException {
        write(umlModel, null, outFile);
    }

    /**
     * Write XMI and (optionally) inject stereotype applications based on the extracted {@link JModel}.
     */
    public static void write(Model umlModel, JModel jModel, Path outFile) throws IOException {
        if (umlModel == null) {
            throw new IllegalArgumentException("umlModel must not be null");
        }
        if (outFile == null) {
            throw new IllegalArgumentException("outFile must not be null");
        }

        Path parent = outFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String wrapped = writeToString(umlModel, jModel);
        Files.writeString(outFile, wrapped, StandardCharsets.UTF_8);
    }

    /**
     * Serialize a UML2 {@link Model} to a deterministic XMI string.
     *
     * <p>This is useful for server-mode usage where callers want the XMI in-memory rather than
     * writing to disk.</p>
     */
    public static String writeToString(Model umlModel, JModel jModel) throws IOException {
        if (umlModel == null) {
            throw new IllegalArgumentException("umlModel must not be null");
        }

        // Ensure UML package is initialized
        UMLPackage.eINSTANCE.eClass();

        ResourceSet resourceSet = new ResourceSetImpl();
        // Register UML in the package registry
        resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);

        // Use a dummy URI (we won't write to disk).
        URI uri = URI.createURI("memory:/model.xmi");
        Resource resource = new XMIResourceImpl(uri);
        resource.getContents().add(umlModel);

        // Deterministic IDs: set explicit xmi:ids on the resource before save.
        if (resource instanceof XMLResource) {
            assignDeterministicIds((XMLResource) resource, umlModel);
        }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put(XMLResource.OPTION_ENCODING, "UTF-8");
        options.put(XMLResource.OPTION_FORMATTED, Boolean.TRUE);
        options.put(XMLResource.OPTION_SAVE_TYPE_INFORMATION, Boolean.FALSE);
        options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.FALSE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resource.save(baos, options);
        String xml = baos.toString(StandardCharsets.UTF_8);

        // Always produce a wrapped <xmi:XMI> document for maximum tool compatibility.
        String wrapped = ensureXmiWrapper(xml);

        if (jModel != null) {
            wrapped = StereotypeXmiInjector.inject(umlModel, jModel, wrapped);
        }
        return wrapped;
    }

    private static String ensureXmiWrapper(String xml) {
        String trimmed = xml.trim();
        if (trimmed.startsWith("<xmi:XMI") || trimmed.contains("<xmi:XMI")) {
            // Already wrapped. Do not force-add any extra namespaces here; the stereotype injector
            // will add the j2x namespace only when it actually injects stereotype applications.
            return xml;
        }

        // Strip XML declaration if present
        String inner = xml;
        if (inner.startsWith("<?xml")) {
            int idx = inner.indexOf("?>");
            if (idx >= 0) {
                inner = inner.substring(idx + 2);
            }
        }
        inner = inner.stripLeading();
        StringBuilder wrapped = new StringBuilder();
        wrapped.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        wrapped.append("<xmi:XMI xmi:version=\"2.1\" xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\" ");
        wrapped.append("xmlns:uml=\"http://www.eclipse.org/uml2/3.0.0/UML\" ");
        wrapped.append(">\n");
        // indent inner document for readability
        for (String line : inner.split("\\R", -1)) {
            if (line.isEmpty()) continue;
            wrapped.append("  ").append(line).append("\n");
        }
        wrapped.append("</xmi:XMI>\n");
        return wrapped.toString();
    }


    private static void assignDeterministicIds(XMLResource resource, Model umlModel) {
        // Root first
        setId(resource, umlModel, idFromAnnotationOrFallback(umlModel));

        // Then all contents in stable traversal order (EMF eAllContents is stable given stable containment).
        TreeIterator<EObject> it = umlModel.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            String id = idFromAnnotationOrFallback(obj);
            setId(resource, obj, id);
        }
    }

    private static void setId(XMLResource resource, EObject obj, String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) {
            return;
        }
        // Ensure valid XML ID token. XMI ids must be valid XML IDs (Name tokens) and must not
        // contain characters like '<' or '>' (e.g. generics in type names). Keep determinism.
        String id = sanitizeXmiId(rawId);
        resource.setID(obj, id);
    }

    /**
     * Sanitize a deterministic id string into a valid XML ID (Name).
     *
     * <p>Allowed characters for XML Names: letters, digits, '.', '-', '_', ':' (plus some unicode).
     * To be conservative and tool-friendly, we restrict to ASCII name chars and replace everything
     * else with '_'. When sanitization changes the id, we append a short stable hash suffix to
     * minimize collisions.</p>
     */
    private static String sanitizeXmiId(String raw) {
        String base = raw == null ? "" : raw.trim();
        if (base.isEmpty()) {
            return "_" + shortSha256Hex("empty");
        }

        // Replace any illegal chars (including '<', '>', spaces, quotes, etc.).
        String sanitized = base.replaceAll("[^A-Za-z0-9_.:-]", "_");

        // XML Name must start with a letter or underscore (we choose underscore).
        if (!sanitized.matches("^[A-Za-z_].*")) {
            sanitized = "_" + sanitized;
        }

        // Keep ids reasonably short for tool compatibility.
        boolean truncated = false;
        int maxLen = 120;
        if (sanitized.length() > maxLen) {
            sanitized = sanitized.substring(0, maxLen);
            truncated = true;
        }

        // If we changed the string (or truncated), append a short stable suffix to reduce collisions.
        if (!sanitized.equals(base) || truncated) {
            String h = shortSha256Hex(base);
            String suffix = h.length() > 10 ? h.substring(0, 10) : h;
            // Avoid exceeding max length too much.
            int room = Math.max(0, maxLen - sanitized.length());
            if (room < (1 + suffix.length())) {
                int keep = Math.max(1, sanitized.length() - ((1 + suffix.length()) - room));
                sanitized = sanitized.substring(0, keep);
            }
            sanitized = sanitized + "_" + suffix;
        }

        // Always prefix '_' for safety across tools that prefer underscore-start IDs.
        if (!sanitized.startsWith("_")) {
            sanitized = "_" + sanitized;
        }

        return sanitized;
    }

    private static String idFromAnnotationOrFallback(EObject obj) {
        String ann = getAnnotatedId(obj);
        if (ann != null && !ann.trim().isEmpty()) {
            return ann;
        }
        // Fallback: stable hash of containment path.
        // IMPORTANT: do not use traversal index salts here, because traversal order can change
        // when containment changes (e.g. nested types). Using only the containment path keeps
        // the fallback deterministic for a given containment structure.
        String basis = stablePath(obj);
        return shortSha256Hex(basis);
    }

    private static String getAnnotatedId(EObject obj) {
        if (obj instanceof org.eclipse.uml2.uml.Element) {
            org.eclipse.uml2.uml.Element el = (org.eclipse.uml2.uml.Element) obj;
            EAnnotation ann = el.getEAnnotation(ID_ANNOTATION_SOURCE);
            if (ann != null) {
                String v = ann.getDetails().get(ID_ANNOTATION_KEY);
                if (v != null && !v.trim().isEmpty()) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    private static String stablePath(EObject obj) {
        // Build a containment path from root -> leaf.
        // Uses: eClass name + (optional NamedElement.name) + index within container contents.
        StringBuilder sb = new StringBuilder();
        EObject cur = obj;
        while (cur != null) {
            if (sb.length() > 0) sb.insert(0, "/");
            sb.insert(0, segment(cur));
            cur = cur.eContainer();
        }
        return sb.toString();
    }

    private static String segment(EObject obj) {
        String cls = obj.eClass().getName();
        String name = null;
        if (obj instanceof NamedElement) {
            name = ((NamedElement) obj).getName();
        }

        int idx = -1;
        EObject parent = obj.eContainer();
        if (parent != null) {
            try {
                idx = parent.eContents().indexOf(obj);
            } catch (Exception ignore) {
                idx = -1;
            }
        }

        if (name != null && name.trim().length() > 0) {
            return cls + ":" + name.trim() + "#" + idx;
        }
        return cls + "#" + idx;
    }

    private static String shortSha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dig.length; i++) {
                int b = dig[i] & 0xff;
                String hx = Integer.toHexString(b);
                if (hx.length() == 1) sb.append('0');
                sb.append(hx);
            }
            // 24 hex chars to reduce collision risk while keeping IDs short.
            return sb.substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            return Integer.toHexString(s.hashCode());
        }
    }
}

package se.erland.javatoxmi.xmi;

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
import se.erland.javatoxmi.model.JModel;

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

        // Ensure UML package is initialized
        UMLPackage.eINSTANCE.eClass();

        ResourceSet resourceSet = new ResourceSetImpl();

        // Register UML in the package registry
        resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);

        // Use XMIResourceImpl to ensure <xmi:XMI> root wrapper (some tools expect this).

        URI uri = URI.createFileURI(outFile.toAbsolutePath().toString());
        Resource resource = new XMIResourceImpl(uri);
        resource.getContents().add(umlModel);

        // Deterministic IDs: set explicit xmi:ids on the resource before save.
        if (resource instanceof XMLResource) {
            assignDeterministicIds((XMLResource) resource, umlModel);
        }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put(XMLResource.OPTION_ENCODING, "UTF-8");
        options.put(XMLResource.OPTION_FORMATTED, Boolean.TRUE);

        // Avoid non-deterministic metadata.
        options.put(XMLResource.OPTION_SAVE_TYPE_INFORMATION, Boolean.FALSE);
        options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.FALSE);
        // Do not generate UUID-based IDs (some older EMF versions ignore/omit this option).
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resource.save(baos, options);
        String xml = baos.toString(StandardCharsets.UTF_8);

        // Always produce a wrapped <xmi:XMI> document for maximum tool compatibility.
        String wrapped = ensureXmiWrapper(xml);


        // Optional: inject stereotype applications via XMI extension.
        if (jModel != null) {
            wrapped = StereotypeXmiInjector.inject(umlModel, jModel, wrapped);
        }

        Files.writeString(outFile, wrapped, StandardCharsets.UTF_8);
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
        setId(resource, umlModel, idFromAnnotationOrFallback(umlModel, "root"));

        // Then all contents in stable traversal order (EMF eAllContents is stable given stable containment).
        TreeIterator<EObject> it = umlModel.eAllContents();
        int i = 0;
        while (it.hasNext()) {
            EObject obj = it.next();
            String id = idFromAnnotationOrFallback(obj, "n" + i);
            setId(resource, obj, id);
            i++;
        }
    }

    private static void setId(XMLResource resource, EObject obj, String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) {
            return;
        }
        // Ensure valid XML ID token. Prefix with '_' to be safe.
        String id = rawId.startsWith("_") ? rawId : "_" + rawId;
        resource.setID(obj, id);
    }

    private static String idFromAnnotationOrFallback(EObject obj, String fallbackSalt) {
        String ann = getAnnotatedId(obj);
        if (ann != null && !ann.trim().isEmpty()) {
            return ann;
        }
        // Fallback: stable hash of containment path.
        String basis = fallbackSalt + ":" + stablePath(obj);
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

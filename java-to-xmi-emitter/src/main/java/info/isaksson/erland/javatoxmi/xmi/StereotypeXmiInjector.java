package info.isaksson.erland.javatoxmi.xmi;

import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;

import java.util.List;

/**
 * Step 5 (recommended approach) — Apply stereotypes by injecting deterministic, UML2-style
 * stereotype applications into the serialized XMI.
 *
 * <p>Originally this injector was hard-coded to a single profile (JavaAnnotations). It now supports
 * injecting applications for multiple profiles, including profiles materialized from IR
 * stereotypeDefinitions (v2 IR format).</p>
 */
final class StereotypeXmiInjector {

    private StereotypeXmiInjector() {}

    static String inject(Model umlModel, JModel jModel, String xmiWrappedXml) {
        if (umlModel == null || jModel == null) return xmiWrappedXml;

        List<Profile> profiles = ProfileApplicationInjector.findAllProfiles(umlModel);
        if (profiles.isEmpty()) return xmiWrappedXml;

        String appsXml = StereotypeApplicationInjector.buildApplicationsXml(umlModel, jModel, profiles);
        if (appsXml == null || appsXml.isBlank()) return xmiWrappedXml;

        // Ensure each profile prefix namespace is declared and each profile has a profileApplication.
        for (Profile p : profiles) {
            if (p == null) continue;
            String prefix = p.getName();
            if (prefix == null || prefix.isBlank()) continue;

            String uri = profileUri(p);
            if (uri == null || uri.isBlank()) continue;

            xmiWrappedXml = ProfileApplicationInjector.ensureProfileNamespaceDeclared(xmiWrappedXml, prefix, uri);
            xmiWrappedXml = ProfileApplicationInjector.ensureProfileApplicationPresent(xmiWrappedXml, p);
        }

        return XmiDomUtil.injectBeforeClosingXmi(xmiWrappedXml, appsXml);
    }

    private static String profileUri(Profile p) {
        if (p == null) return null;

        String name = p.getName() == null ? "" : p.getName().trim();
        if (!name.isBlank() && JavaAnnotationProfileBuilder.PROFILE_NAME.equals(name)) {
            return JavaAnnotationProfileBuilder.PROFILE_URI;
        }

        // Eclipse UML2 Profile does not expose a stable "URI" property across versions.
        // For IR-created profiles, use a deterministic fallback namespace based on profile name.
        String safe = name.replaceAll("[^A-Za-z0-9_.-]+", "_");
        if (safe.isBlank()) safe = "IRProfile";
        return "http://java-to-xmi/schemas/ir/" + safe + "/1.0";
    }
}

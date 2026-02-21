package info.isaksson.erland.javatoxmi.xmi;

import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Profile;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;

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

    private StereotypeXmiInjector() {}

    static String inject(Model umlModel, JModel jModel, String xmiWrappedXml) {
        if (umlModel == null || jModel == null) return xmiWrappedXml;

        Profile profile = ProfileApplicationInjector.findJavaAnnotationsProfile(umlModel);
        if (profile == null) return xmiWrappedXml;

        String appsXml = StereotypeApplicationInjector.buildApplicationsXml(umlModel, jModel, profile);
        if (appsXml == null || appsXml.isBlank()) return xmiWrappedXml;

        // Ensure the profile Ecore namespace is declared for stereotype application element names.

        xmiWrappedXml = ProfileApplicationInjector.ensureProfileNamespaceDeclared(xmiWrappedXml);
        xmiWrappedXml = ProfileApplicationInjector.ensureProfileApplicationPresent(xmiWrappedXml, profile);

        return XmiDomUtil.injectBeforeClosingXmi(xmiWrappedXml, appsXml);
    }
}

package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.ir.IrRuntime;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;

import java.util.List;

/**
 * Ensures a stable set of runtime semantics stereotypes exist in the JavaAnnotations profile.
 *
 * <p>This is intentionally conservative: it only guarantees that stereotypes exist and extend the
 * correct UML metaclasses. Tagged values for runtime semantics are persisted via the existing
 * tool-tags mechanism (java-to-xmi:tags -> J2XTags stereotype application at XMI time).</p>
 */
final class UmlRuntimeProfileApplicator {

    void applyRuntimeProfile(UmlBuildContext ctx) {
        if (ctx == null || ctx.model == null) return;

        JavaAnnotationProfileBuilder pb = new JavaAnnotationProfileBuilder();
        Profile profile = pb.ensureProfile(ctx.model);

        // IMPORTANT: UML2 only allows stereotypes to be applied when the owning Profile
        // is applied to the nearest package (here: the root Model). Without this, calls
        // like element.applyStereotype(...) will fail and runtime stereotypes won't show up.

        JavaAnnotationMetaclassExtensionHelper ext = new JavaAnnotationMetaclassExtensionHelper();

        // Class-level stereotypes
        ensure(profile, pb, ext, IrRuntime.ST_REST_RESOURCE, "Class");
        ensure(profile, pb, ext, IrRuntime.ST_INTERCEPTOR, "Class");

        // Operation-level stereotypes
        ensure(profile, pb, ext, IrRuntime.ST_REST_OPERATION, "Operation");
        ensure(profile, pb, ext, IrRuntime.ST_MESSAGE_CONSUMER, "Operation");
        ensure(profile, pb, ext, IrRuntime.ST_MESSAGE_PRODUCER, "Operation");
        ensure(profile, pb, ext, IrRuntime.ST_SCHEDULED_JOB, "Operation");

        // Dependency-level stereotypes
        ensure(profile, pb, ext, IrRuntime.ST_FIRES_EVENT, "Dependency");
        ensure(profile, pb, ext, IrRuntime.ST_OBSERVES_EVENT, "Dependency");

        // Artifact / package stereotypes
        ensure(profile, pb, ext, IrRuntime.ST_FLYWAY_MIGRATION, "Artifact");
        ensure(profile, pb, ext, IrRuntime.ST_JAVA_MODULE, "Package");

        // Transaction boundary can apply broadly.
        ensure(profile, pb, ext, IrRuntime.ST_TRANSACTIONAL, "NamedElement");

        // Apply the profile *after* ensuring stereotypes/extensions are defined,
        // otherwise UML2 may not treat them as applicable on elements.
        tryApplyProfile(ctx.model, profile);
    }

    private static void tryApplyProfile(org.eclipse.uml2.uml.Model model, Profile profile) {
        if (model == null || profile == null) return;
        try {
            if (model.getAppliedProfiles() != null && model.getAppliedProfiles().contains(profile)) return;
            model.applyProfile(profile);
        } catch (Throwable ignored) {
            // Best-effort. If this fails, runtime stereotypes simply won't be applied.
        }
    }

    private static void ensure(Profile profile,
                               JavaAnnotationProfileBuilder pb,
                               JavaAnnotationMetaclassExtensionHelper ext,
                               String stereotypeName,
                               String metaclassName) {
        if (profile == null || pb == null || ext == null) return;
        if (stereotypeName == null || stereotypeName.isBlank()) return;
        if (metaclassName == null || metaclassName.isBlank()) return;

        // QualifiedName is best-effort; keep it stable.
        String qn = "runtime:" + stereotypeName;
        Stereotype st = pb.ensureStereotype(profile, stereotypeName, qn);
        ext.ensureStereotypeExtendsMetaclass(profile, st, metaclassName, false);

        // Determinism: ensure stereotype is owned and stable; no attributes needed here.
        // (Tags are stored via J2XTags tool-tags stereotype applications.)
        if (profile.getOwnedStereotype(stereotypeName) == null) {
            // Shouldn't happen, but keep a defensive read.
            profile.getOwnedStereotypes().add(st);
        }
    }
}
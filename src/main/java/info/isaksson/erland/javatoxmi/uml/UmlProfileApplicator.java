package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.JTypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 3/4: Build the JavaAnnotations UML Profile and ensure needed stereotypes/attributes/extensions exist.
 */
final class UmlProfileApplicator {

    void applyJavaAnnotationProfile(UmlBuildContext ctx, List<JType> types) {
        JavaAnnotationProfileBuilder profileBuilder = new JavaAnnotationProfileBuilder();
        org.eclipse.uml2.uml.Profile profile = profileBuilder.ensureProfile(ctx.model);

        // Determinism: apply in a stable order (types, annotations, and tag keys sorted).
        for (JType t : types) {
            if (t.annotations == null || t.annotations.isEmpty()) continue;

            JavaAnnotationProfileBuilder.MetaclassTarget target;
            if (t.kind == JTypeKind.INTERFACE) {
                target = JavaAnnotationProfileBuilder.MetaclassTarget.INTERFACE;
            } else if (t.kind == JTypeKind.ENUM) {
                target = JavaAnnotationProfileBuilder.MetaclassTarget.ENUMERATION;
            } else {
                target = JavaAnnotationProfileBuilder.MetaclassTarget.CLASS;
            }

            List<JAnnotationUse> anns = new ArrayList<>(t.annotations);
            anns.sort((a, b) -> {
                String aq = a == null ? "" : (a.qualifiedName != null ? a.qualifiedName : (a.simpleName != null ? a.simpleName : ""));
                String bq = b == null ? "" : (b.qualifiedName != null ? b.qualifiedName : (b.simpleName != null ? b.simpleName : ""));
                return aq.compareTo(bq);
            });

            for (JAnnotationUse ann : anns) {
                if (ann == null || ann.simpleName == null || ann.simpleName.isBlank()) continue;

                org.eclipse.uml2.uml.Stereotype st = profileBuilder.ensureStereotype(profile, ann.simpleName, ann.qualifiedName);
                profileBuilder.ensureMetaclassExtension(profile, st, target);

                if (ann.values != null) {
                    List<String> keys = new ArrayList<>(ann.values.keySet());
                    keys.sort(String::compareTo);
                    for (String k : keys) {
                        if (k == null || k.isBlank()) continue;
                        profileBuilder.ensureStringAttribute(profile, st, k);
                    }
                }
            }
        }
    }
}

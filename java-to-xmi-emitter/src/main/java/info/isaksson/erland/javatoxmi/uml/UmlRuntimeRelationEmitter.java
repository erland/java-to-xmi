package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JRuntimeRelation;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Emits runtime semantic relations as stereotyped UML Dependencies.
 */
final class UmlRuntimeRelationEmitter {

    void emit(UmlBuildContext ctx, List<JRuntimeRelation> relations) {
        if (ctx == null || ctx.model == null) return;
        if (relations == null || relations.isEmpty()) return;

        // Deterministic emission order.
        List<JRuntimeRelation> sorted = new ArrayList<>(relations);
        sorted.sort(Comparator
                .comparing((JRuntimeRelation r) -> nullSafe(r.sourceQualifiedName))
                .thenComparing(r -> nullSafe(r.targetQualifiedName))
                .thenComparing(r -> nullSafe(r.stereotype))
                .thenComparing(r -> nullSafe(r.name))
                .thenComparing(r -> nullSafe(r.id)));

        Profile profile = ctx.model.getAppliedProfile(JavaAnnotationProfileBuilder.PROFILE_NAME);
        if (profile == null) {
            // Best-effort: try to find by name among owned elements.
            var pe = ctx.model.getPackagedElement(JavaAnnotationProfileBuilder.PROFILE_NAME);
            if (pe instanceof Profile) profile = (Profile) pe;
        }

        for (JRuntimeRelation r : sorted) {
            if (r == null) continue;
            if (r.sourceQualifiedName == null || r.sourceQualifiedName.isBlank()) continue;
            if (r.targetQualifiedName == null || r.targetQualifiedName.isBlank()) continue;

            NamedElement src = resolveNamed(ctx, r.sourceQualifiedName);
            NamedElement tgt = resolveNamed(ctx, r.targetQualifiedName);
            if (src == null || tgt == null) continue;

            Dependency dep = src.createDependency(tgt);
            if (r.name != null && !r.name.isBlank()) {
                dep.setName(r.name.trim());
            } else if (r.stereotype != null && !r.stereotype.isBlank()) {
                // Give dependencies a stable name when none is provided.
                dep.setName(r.stereotype.trim());
            }

            // Deterministic id for this emitted edge.
            String st = (r.stereotype == null ? "" : r.stereotype.trim());
            UmlBuilderSupport.annotateId(dep, "RuntimeDependency:" + r.sourceQualifiedName + "->" + r.targetQualifiedName + ":" + st);

            // Persist desired runtime stereotype name for XMI post-processing (even if UML2 apply fails).
            if (r.stereotype != null && !r.stereotype.isBlank()) {
                UmlBuilderSupport.annotateRuntimeStereotype(dep, r.stereotype.trim());
            }

            // Apply stereotype (if present + available).
            if (profile != null && r.stereotype != null && !r.stereotype.isBlank()) {
                final String stName = r.stereotype.trim();
                Stereotype stEl = null;

                // Prefer an applicable stereotype lookup (requires Profile to be applied).
                try {
                    stEl = dep.getApplicableStereotype(profile.getName() + "::" + stName);
                } catch (Throwable ignored) {
                }

                // Fallback: match by simple name among applicable stereotypes.
                if (stEl == null) {
                    try {
                        for (Stereotype s : dep.getApplicableStereotypes()) {
                            if (s != null && stName.equals(s.getName())) {
                                stEl = s;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                // Final fallback: owned stereotype (may not be applicable if profile not applied).
                if (stEl == null) {
                    try {
                        stEl = profile.getOwnedStereotype(stName);
                    } catch (Throwable ignored) {
                    }
                }

                if (stEl != null) {
                    try {
                        dep.applyStereotype(stEl);
                    } catch (Throwable ignored) {
                        // Keep dependency even if stereotype application fails.
                    }
                }
            }

            // Persist tags via tool-tags annotation; injected as J2XTags during XMI writing.
            if (r.tags != null && !r.tags.isEmpty()) {
                UmlBuilderSupport.annotateTags(dep, r.tags);
            }
        }
    }

    private static NamedElement resolveNamed(UmlBuildContext ctx, String qn) {
        if (ctx == null || qn == null) return null;

        var c = ctx.classifierByQName.get(qn);
        if (c != null) return c;

        var p = ctx.packageByName.get(qn);
        if (p != null) return p;

        // Allow referencing the root model by its name.
        if (ctx.model != null && qn.equals(ctx.model.getName())) return ctx.model;

        return null;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
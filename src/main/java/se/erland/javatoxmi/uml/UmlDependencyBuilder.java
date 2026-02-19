package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.NamedElement;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds conservative method-signature dependencies.
 */
final class UmlDependencyBuilder {

    void addMethodSignatureDependencies(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;

        Set<String> deps = new HashSet<>();
        for (JMethod m : t.methods) {
            if (m.returnType != null && !m.returnType.isBlank() && !"void".equals(m.returnType)) {
                deps.add(UmlAssociationBuilder.stripGenerics(m.returnType));
            }
            for (JParam p : m.params) {
                if (p.type == null || p.type.isBlank()) continue;
                deps.add(UmlAssociationBuilder.stripGenerics(p.type));
            }
        }

        List<String> sorted = new ArrayList<>(deps);
        Collections.sort(sorted);
        for (String depType : sorted) {
            upsertDependency(ctx, classifier, t.qualifiedName, depType, "signature");
        }
    }

    void addMethodBodyDependencies(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;
        if (t.methodBodyTypeDependencies == null || t.methodBodyTypeDependencies.isEmpty()) return;

        // De-duplicate by normalized target type so we never create multiple Dependency edges
        // between the same two classifiers (even if discovered multiple times).
        Set<String> deps = new HashSet<>();
        for (String depTypeRaw : t.methodBodyTypeDependencies) {
            if (depTypeRaw == null || depTypeRaw.isBlank()) continue;
            deps.add(UmlAssociationBuilder.stripGenerics(depTypeRaw));
        }

        List<String> sorted = new ArrayList<>(deps);
        Collections.sort(sorted);
        for (String depType : sorted) {
            upsertDependency(ctx, classifier, t.qualifiedName, depType, "invocation");
        }
    }

    private static void upsertDependency(UmlBuildContext ctx,
                                        Classifier from,
                                        String fromQName,
                                        String toQName,
                                        String kind) {
        if (ctx == null || from == null) return;
        if (toQName == null || toQName.isBlank()) return;

        Classifier target = ctx.classifierByQName.get(toQName);
        if (target == null) return;
        if (target == from) return;
        if (ctx.hasAssociationBetween(from, target)) return;

        Dependency existing = findExistingDependency(from, target);
        if (existing == null) {
            Dependency d = from.createDependency((NamedElement) target);
            ctx.stats.dependenciesCreated++;
            // One deterministic edge per (from,to) pair.
            UmlBuilderSupport.annotateId(d, "Dependency:" + fromQName + "->" + toQName);
            // Preserve what kind(s) of evidence produced this dependency.
            UmlBuilderSupport.annotateTags(d, Map.of("dep." + kind, "true"));
        } else {
            // Ensure the dependency carries evidence tags without creating duplicates.
            UmlBuilderSupport.annotateTags(existing, Map.of("dep." + kind, "true"));
        }
    }

    private static Dependency findExistingDependency(Classifier from, Classifier target) {
        if (from == null || target == null) return null;
        for (Dependency d : from.getClientDependencies()) {
            if (d == null) continue;
            for (NamedElement sup : d.getSuppliers()) {
                if (sup == target) return d;
            }
        }
        return null;
    }
}

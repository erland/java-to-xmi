package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.NamedElement;
import info.isaksson.erland.javatoxmi.model.JMethod;
import info.isaksson.erland.javatoxmi.model.JParam;
import info.isaksson.erland.javatoxmi.model.JType;

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
                deps.add(normalizeTypeName(m.returnType));
            }
            for (JParam p : m.params) {
                if (p.type == null || p.type.isBlank()) continue;
                deps.add(normalizeTypeName(p.type));
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
            deps.add(normalizeTypeName(depTypeRaw));
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

        // Normalization MUST match type resolution elsewhere (e.g., parameters/attributes),
        // otherwise dependencies for arrays ("Foo[]") would never resolve the target classifier.
        toQName = normalizeTypeName(toQName);
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

    /**
     * Normalize a type name string into something that can be looked up in {@code ctx.classifierByQName}.
     *
     * <p>We currently store classifiers by their qualified name (without generics and without array suffixes).
     * This is intentionally conservative and matches how parameter/attribute types are resolved.</p>
     */
    private static String normalizeTypeName(String raw) {
        if (raw == null) return null;
        String t = UmlAssociationBuilder.stripGenerics(raw).trim();
        // Remove repeated array suffixes: Foo[][] -> Foo
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2).trim();
        }
        return t;
    }
}

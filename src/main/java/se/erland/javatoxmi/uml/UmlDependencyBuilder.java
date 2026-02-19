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
            Classifier target = ctx.classifierByQName.get(depType);
            if (target == null) continue;
            if (target == classifier) continue;
            Dependency d = classifier.createDependency((NamedElement) target);
            ctx.stats.dependenciesCreated++;
            UmlBuilderSupport.annotateId(d, "Dependency:" + t.qualifiedName + "->" + depType);
        }
    }

    void addMethodBodyDependencies(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;
        if (t.methodBodyTypeDependencies == null || t.methodBodyTypeDependencies.isEmpty()) return;

        List<String> deps = new ArrayList<>(t.methodBodyTypeDependencies);
        Collections.sort(deps);
        for (String depTypeRaw : deps) {
            String depType = UmlAssociationBuilder.stripGenerics(depTypeRaw);
            Classifier target = ctx.classifierByQName.get(depType);
            if (target == null) continue;
            if (target == classifier) continue;
            Dependency d = classifier.createDependency((NamedElement) target);
            ctx.stats.dependenciesCreated++;
            UmlBuilderSupport.annotateId(d, "DependencyCall:" + t.qualifiedName + "->" + depType);
            UmlBuilderSupport.annotateTags(d, Map.of("kind", "invocation"));
        }
    }
}

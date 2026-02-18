package se.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a UML object graph from the extracted Java IR.
 *
 * <p>Uses Eclipse UML2 (EMF) to create a well-formed UML model in-memory.
 * Serialization to XMI is handled elsewhere.</p>
 */
public final class UmlBuilder {
    public static final String ID_ANNOTATION_SOURCE = "java-to-xmi:id";
    public static final String TAGS_ANNOTATION_SOURCE = "java-to-xmi:tags";

    private final MultiplicityResolver multiplicityResolver = new MultiplicityResolver();

    public static final class Result {
        public final Model umlModel;
        public final UmlBuildStats stats;

        private Result(Model umlModel, UmlBuildStats stats) {
            this.umlModel = umlModel;
            this.stats = stats;
        }
    }

    public Result build(JModel jModel, String modelName) {
        return build(jModel, modelName, true);
    }

    /**
     * Build UML model.
     *
     * @param includeStereotypes when false, skips building the JavaAnnotations profile and avoids
     *                           stereotype-related output (backwards-compat mode).
     */
    public Result build(JModel jModel, String modelName, boolean includeStereotypes) {
        return build(jModel, modelName, includeStereotypes, AssociationPolicy.RESOLVED);
    }

    /**
     * Build UML model.
     *
     * @param includeStereotypes when false, skips building the JavaAnnotations profile and avoids
     *                           stereotype-related output (backwards-compat mode).
     * @param associationPolicy controls whether fields become UML Associations or stay attribute-only.
     */
    public Result build(JModel jModel, String modelName, boolean includeStereotypes, AssociationPolicy associationPolicy) {
        Objects.requireNonNull(jModel, "jModel");
        if (modelName == null || modelName.isBlank()) modelName = "JavaModel";
        AssociationPolicy ap = associationPolicy == null ? AssociationPolicy.RESOLVED : associationPolicy;

        UmlBuildStats stats = new UmlBuildStats();

        // Ensure UML resource package is registered (important for later XMI writing).
        UMLPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);

        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName(modelName);
        UmlBuilderSupport.annotateId(model, "Model:" + modelName);

        UmlBuildContext ctx = new UmlBuildContext(model, stats, multiplicityResolver, ap);

        UmlClassifierBuilder classifierBuilder = new UmlClassifierBuilder();
        UmlFeatureBuilder featureBuilder = new UmlFeatureBuilder(classifierBuilder);
        UmlInheritanceBuilder inheritanceBuilder = new UmlInheritanceBuilder();
        UmlAssociationBuilder associationBuilder = new UmlAssociationBuilder();
        UmlDependencyBuilder dependencyBuilder = new UmlDependencyBuilder();
        UmlProfileApplicator profileApplicator = new UmlProfileApplicator();

        // 1) Packages (deterministic)
        Set<String> pkgNames = new HashSet<>();
        for (JType t : jModel.types) {
            if (t.packageName != null && !t.packageName.isBlank()) pkgNames.add(t.packageName);
        }
        List<String> pkgList = new ArrayList<>(pkgNames);
        Collections.sort(pkgList);
        for (String pkg : pkgList) {
            classifierBuilder.getOrCreatePackage(ctx, pkg);
        }

        // 2) Classifiers
        // Create top-level types first, then nested types in increasing nesting depth.
        // This guarantees enclosing classifiers exist before we attach nested classifiers.
        List<JType> types = new ArrayList<>(jModel.types);
        types.sort(Comparator.comparing(t -> t.qualifiedName));

        List<JType> topLevel = new ArrayList<>();
        List<JType> nested = new ArrayList<>();
        for (JType t : types) {
            if (t.isNested) nested.add(t);
            else topLevel.add(t);
        }

        for (JType t : topLevel) {
            classifierBuilder.createClassifier(ctx, t);
        }

        nested.sort(Comparator
                .comparingInt((JType t) -> nestingDepth(t.qualifiedName))
                .thenComparing(t -> t.qualifiedName));

        for (JType t : nested) {
            classifierBuilder.createClassifier(ctx, t);
        }

        // 3) Features (fields/methods)
        for (JType t : types) {
            Classifier c = ctx.classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            featureBuilder.addFeatures(ctx, c, t);
        }

        // 4) Relationships
        for (JType t : types) {
            Classifier c = ctx.classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            inheritanceBuilder.addInheritanceAndRealization(ctx, c, t);
        }
        for (JType t : types) {
            Classifier c = ctx.classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            associationBuilder.addFieldAssociations(ctx, c, t);
            dependencyBuilder.addMethodSignatureDependencies(ctx, c, t);
        }

        // Profile + stereotypes
        if (includeStereotypes) {
            // Always build the profile when stereotypes are enabled.
            profileApplicator.applyJavaAnnotationProfile(ctx, types);
        }

        return new Result(model, stats);
    }

    private static int nestingDepth(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) return 0;
        // Depth is the number of segments after the package. We don't have the package here,
        // but using total segments works fine for ordering (Outer < Outer.Inner < Outer.Inner.Deep).
        int dots = 0;
        for (int i = 0; i < qualifiedName.length(); i++) {
            if (qualifiedName.charAt(i) == '.') dots++;
        }
        return dots;
    }
}

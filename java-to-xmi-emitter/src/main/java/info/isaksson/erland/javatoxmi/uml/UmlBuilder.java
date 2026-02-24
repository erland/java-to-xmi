package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JRuntimeAnnotation;
import info.isaksson.erland.javatoxmi.model.JType;

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
    public static final String RUNTIME_STEREOTYPE_ANNOTATION_SOURCE = "java-to-xmi:runtime";
    public static final String RUNTIME_STEREOTYPE_ANNOTATION_KEY = "stereotype";

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
        return build(jModel, modelName, includeStereotypes, associationPolicy, NestedTypesMode.UML);
    }

    /**
     * Build UML model.
     *
     * @param includeStereotypes when false, skips building the JavaAnnotations profile and avoids
     *                           stereotype-related output (backwards-compat mode).
     * @param associationPolicy controls whether fields become UML Associations or stay attribute-only.
     * @param nestedTypesMode controls whether nested Java member types are nested-only, nested+imported into
     *                        the owning Java package, or flattened for backwards compatibility.
     */
    public Result build(JModel jModel,
                        String modelName,
                        boolean includeStereotypes,
                        AssociationPolicy associationPolicy,
                        NestedTypesMode nestedTypesMode) {
        return build(jModel, modelName, includeStereotypes, associationPolicy, nestedTypesMode,
                false, false, false);
    }

    /**
     * Build UML model.
     *
     * @param includeDependencies when true, emits dependency relationships derived from method signatures
     *                            and conservatively from method/constructor bodies (approximate call graph).
     *                            Dependencies that duplicate existing associations are suppressed.
     */
    public Result build(JModel jModel,
                        String modelName,
                        boolean includeStereotypes,
                        AssociationPolicy associationPolicy,
                        NestedTypesMode nestedTypesMode,
                        boolean includeDependencies,
                        boolean includeAccessors,
                        boolean includeConstructors) {
        Objects.requireNonNull(jModel, "jModel");
        if (modelName == null || modelName.isBlank()) modelName = "JavaModel";
        AssociationPolicy ap = associationPolicy == null ? AssociationPolicy.RESOLVED : associationPolicy;
        NestedTypesMode ntm = nestedTypesMode == null ? NestedTypesMode.UML : nestedTypesMode;

        UmlBuildStats stats = new UmlBuildStats();

        // Ensure UML resource package is registered (important for later XMI writing).
        UMLPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);

        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName(modelName);
        UmlBuilderSupport.annotateId(model, "Model:" + modelName);

        UmlBuildContext ctx = new UmlBuildContext(model, stats, multiplicityResolver, ap, ntm,
                includeDependencies, includeAccessors, includeConstructors);

        UmlClassifierBuilder classifierBuilder = new UmlClassifierBuilder();
        UmlFeatureBuilder featureBuilder = new UmlFeatureBuilder(classifierBuilder);
        UmlInheritanceBuilder inheritanceBuilder = new UmlInheritanceBuilder();
        UmlAssociationBuilder associationBuilder = new UmlAssociationBuilder();
        UmlDependencyBuilder dependencyBuilder = new UmlDependencyBuilder();
        UmlRuntimeRelationEmitter runtimeRelationEmitter = new UmlRuntimeRelationEmitter();
        UmlPackageImportBuilder packageImportBuilder = new UmlPackageImportBuilder();
        UmlProfileApplicator profileApplicator = new UmlProfileApplicator();
        UmlRuntimeProfileApplicator runtimeProfileApplicator = new UmlRuntimeProfileApplicator();

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

        // Index Java types for later safe association merge heuristics.
        for (JType t : types) {
            if (t == null || t.qualifiedName == null || t.qualifiedName.isBlank()) continue;
            ctx.typeByQName.put(t.qualifiedName, t);
        }

        if (ntm == NestedTypesMode.FLATTEN) {
            // Backwards-compat: treat everything as package-owned.
            for (JType t : types) {
                classifierBuilder.createClassifier(ctx, t);
            }
        } else {
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

            // Step 5 — consumer-facing sanity: optionally mirror nested classifiers into the owning
            // Java package via ElementImport (does not duplicate classifiers).
            if (ntm == NestedTypesMode.UML_IMPORT) {
                for (JType t : nested) {
                    Classifier nestedClassifier = ctx.classifierByQName.get(t.qualifiedName);
                    if (nestedClassifier == null) continue;
                    if (t.packageName == null || t.packageName.isBlank()) continue;
                    org.eclipse.uml2.uml.Package pkg = classifierBuilder.getOrCreatePackage(ctx, t.packageName);
                    UmlBuilderSupport.ensureElementImport(pkg, nestedClassifier);
                }
            }
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
            if (ctx.includeDependencies) {
                dependencyBuilder.addMethodSignatureDependencies(ctx, c, t);
                dependencyBuilder.addMethodBodyDependencies(ctx, c, t);
            }
        }

        // 4c) Runtime semantic relations (stereotyped dependencies)
        if (includeStereotypes && jModel.runtimeRelations != null && !jModel.runtimeRelations.isEmpty()) {
            // Ensure runtime stereotypes exist in the profile before applying.
            runtimeProfileApplicator.applyRuntimeProfile(ctx);
            runtimeRelationEmitter.emit(ctx, jModel.runtimeRelations);
        }

        // 4d) Runtime semantics applied directly to existing elements (REST resources/operations, etc.)
        if (includeStereotypes && jModel.runtimeAnnotations != null && !jModel.runtimeAnnotations.isEmpty()) {
            runtimeProfileApplicator.applyRuntimeProfile(ctx);
            applyRuntimeAnnotations(ctx, jModel.runtimeAnnotations);
        }

        // 4b) Package imports (high-level dependency structure)
        for (JType t : types) {
            Classifier c = ctx.classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            packageImportBuilder.addPackageImports(ctx, t, c);
        }

        // Profile + stereotypes
        if (includeStereotypes) {
            // Always build the profile when stereotypes are enabled.
            runtimeProfileApplicator.applyRuntimeProfile(ctx);
            profileApplicator.applyJavaAnnotationProfile(ctx, types);
        }

        // Step 4 — determinism hardening: ensure every element has a stable java-to-xmi:id
        // annotation so the XMI writer never needs to fall back to traversal-index-based IDs.
        UmlBuilderSupport.ensureAllElementsHaveId(model);

        return new Result(model, stats);
    }

    private static void applyRuntimeAnnotations(UmlBuildContext ctx, List<JRuntimeAnnotation> annos) {
        if (ctx == null || annos == null || annos.isEmpty()) return;

        List<JRuntimeAnnotation> sorted = new ArrayList<>(annos);
        sorted.sort(Comparator
                .comparing((JRuntimeAnnotation a) -> a == null ? "" : (a.targetKey == null ? "" : a.targetKey))
                .thenComparing(a -> a == null ? "" : (a.stereotype == null ? "" : a.stereotype)));

        for (JRuntimeAnnotation a : sorted) {
            if (a == null) continue;
            if (a.targetKey == null || a.targetKey.isBlank()) continue;
            if (a.stereotype == null || a.stereotype.isBlank()) continue;

            org.eclipse.uml2.uml.Element target = null;
            // First: classifier by QName
            var c = ctx.classifierByQName.get(a.targetKey);
            if (c != null) {
                target = c;
            } else {
                // Second: operation by key
                var op = ctx.operationByKey.get(a.targetKey);
                if (op != null) target = op;
            }
            if (target == null) continue;

            UmlBuilderSupport.annotateRuntimeStereotype(target, a.stereotype);
            if (a.tags != null && !a.tags.isEmpty()) {
                UmlBuilderSupport.annotateTags(target, a.tags);
            }
        }
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

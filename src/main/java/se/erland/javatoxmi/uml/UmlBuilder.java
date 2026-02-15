package se.erland.javatoxmi.uml;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.InterfaceRealization;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.VisibilityKind;
import org.eclipse.uml2.uml.resource.UMLResource;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.JTypeKind;
import se.erland.javatoxmi.model.JVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Feature;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.StructuredClassifier;

/**
 * Step 4 — Build a UML object graph from the extracted Java IR.
 *
 * Uses Eclipse UML2 (EMF) to create a well-formed UML model in-memory.
 *
 * Serialization to XMI is intentionally deferred to Step 5.
 */
public final class UmlBuilder {
    public static final String ID_ANNOTATION_SOURCE = "java-to-xmi:id";

    private UmlBuildStats stats = new UmlBuildStats();

    // Deterministic maps
    private final Map<String, Package> packageByName = new HashMap<>();
    private final Map<String, Classifier> classifierByQName = new HashMap<>();

    public static final class Result {
        public final Model umlModel;
        public final UmlBuildStats stats;

        private Result(Model umlModel, UmlBuildStats stats) {
            this.umlModel = umlModel;
            this.stats = stats;
        }
    }

    public Result build(JModel jModel, String modelName) {
        Objects.requireNonNull(jModel, "jModel");
        if (modelName == null || modelName.isBlank()) modelName = "JavaModel";

        // Reset builder state to allow re-use of the same UmlBuilder instance across multiple builds.
        this.stats = new UmlBuildStats();
        this.packageByName.clear();
        this.classifierByQName.clear();

        // Ensure UML resource package is registered (important for later XMI writing).
        UMLPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);

        UMLFactory factory = UMLFactory.eINSTANCE;
        Model model = factory.createModel();
        model.setName(modelName);
        annotateId(model, "Model:" + modelName);

        // 1) Packages
        // We create packages deterministically by sorting package names.
        Set<String> pkgNames = new HashSet<>();
        for (JType t : jModel.types) {
            if (t.packageName != null && !t.packageName.isBlank()) pkgNames.add(t.packageName);
        }
        List<String> pkgList = new ArrayList<>(pkgNames);
        Collections.sort(pkgList);
        for (String pkg : pkgList) {
            getOrCreatePackage(model, pkg);
        }
// 2) Classifiers
        List<JType> types = new ArrayList<>(jModel.types);
        types.sort(Comparator.comparing(t -> t.qualifiedName));
        for (JType t : types) {
            createClassifier(model, t);
        }

        // 3) Features (fields/methods)
        for (JType t : types) {
            Classifier c = classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            addFeatures(c, t);
        }

        // 4) Relationships (generalization/realization/deps/associations)
        for (JType t : types) {
            Classifier c = classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            addInheritanceAndRealization(c, t);
        }
        for (JType t : types) {
            Classifier c = classifierByQName.get(t.qualifiedName);
            if (c == null) continue;
            addStructuralRelations(c, t);
        }

        // Step 3/4: Java annotation profile + stereotypes
        // Build the profile only if there are annotations present.
        boolean hasAnyAnnotations = false;
        for (JType t : types) {
            if (t.annotations != null && !t.annotations.isEmpty()) {
                hasAnyAnnotations = true;
                break;
            }
        }
        if (hasAnyAnnotations) {
            applyJavaAnnotationProfile(model, types);
        }

        return new Result(model, stats);
    }

    private void applyJavaAnnotationProfile(Model model, List<JType> types) {
        JavaAnnotationProfileBuilder profileBuilder = new JavaAnnotationProfileBuilder();
        org.eclipse.uml2.uml.Profile profile = profileBuilder.ensureProfile(model);

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

    private Package getOrCreatePackage(Model root, String javaPackageName) {
        // Empty package maps to root model.
        if (javaPackageName == null || javaPackageName.isBlank()) {
            return root;
        }

        if (packageByName.containsKey(javaPackageName)) {
            return packageByName.get(javaPackageName);
        }

        String[] parts = javaPackageName.split("\\.");
        Package current = root;
        StringBuilder soFar = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) soFar.append('.');
            soFar.append(parts[i]);
            String q = soFar.toString();
            Package existing = packageByName.get(q);
            if (existing != null) {
                current = existing;
                continue;
            }
            Package created = current.createNestedPackage(parts[i]);
            stats.packagesCreated++;
            annotateId(created, "Package:" + q);
            packageByName.put(q, created);
            current = created;
        }

        // Cache full name
        packageByName.put(javaPackageName, current);
        return current;
    }

    private void createClassifier(Model root, JType t) {
        // Owner can be either a Package (top-level) or an enclosing Classifier (nested member type).
        Classifier enclosing = null;
        if (t.outerQualifiedName != null) {
            enclosing = classifierByQName.get(t.outerQualifiedName);
        }

        Classifier classifier;
        if (t.kind == JTypeKind.INTERFACE) {
            Interface i = UMLFactory.eINSTANCE.createInterface();
            i.setName(t.name);
            classifier = i;
        } else if (t.kind == JTypeKind.ENUM) {
            Enumeration e = UMLFactory.eINSTANCE.createEnumeration();
            e.setName(t.name);
            classifier = e;
        } else {
            // CLASS or ANNOTATION -> UML Class (annotation can be stereotyped later)
            Class c = UMLFactory.eINSTANCE.createClass();
            c.setName(t.name);
            c.setIsAbstract(t.isAbstract);
            classifier = c;
        }

        if (enclosing != null) {
            // Nested member type: attach classifier under its enclosing type.
            // UML2 APIs vary by version; getOwnedMembers() is often derived/unmodifiable.
            // Prefer concrete-type nested-classifier support when available.
            boolean added = false;
            try {
                java.lang.reflect.Method m = enclosing.getClass().getMethod("getNestedClassifiers");
                Object v = m.invoke(enclosing);
                if (v instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<org.eclipse.uml2.uml.Classifier> list = (java.util.List<org.eclipse.uml2.uml.Classifier>) v;
                    list.add(classifier);
                    added = true;
                }
            } catch (ReflectiveOperationException ignore) {
                // fall through
            }

            if (!added) {
                // Last-resort fallback (may be unmodifiable in some UML2 versions).
                try {
                    ((org.eclipse.uml2.uml.Namespace) enclosing).getOwnedMembers().add((NamedElement) classifier);
                    added = true;
                } catch (UnsupportedOperationException ignore) {
                    // fall through
                }
            }

            if (!added) {
                throw new IllegalStateException("Unable to attach nested member type '" + t.name + "' to enclosing type '" + t.outerQualifiedName + "'");
            }
        } else {
            Package owner = getOrCreatePackage(root, t.packageName);
            owner.getOwnedTypes().add(classifier);
        }

        stats.classifiersCreated++;
        annotateId((Element) classifier, "Classifier:" + t.qualifiedName);
        classifierByQName.put(t.qualifiedName, classifier);

        // Visibility
        if (classifier instanceof NamedElement) {
            setVisibility((NamedElement) classifier, t.visibility);
        }
    }

    private void addFeatures(Classifier classifier, JType t) {
        // Enum literals
        if (classifier instanceof Enumeration) {
            Enumeration e = (Enumeration) classifier;
            // Preserve declaration order (required by tests and typically what UML tools expect).
            // Determinism is achieved by the extractor producing enumLiterals in source order.
            for (String lit : t.enumLiterals) {
                if (lit == null || lit.isBlank()) continue;
                EnumerationLiteral el = e.createOwnedLiteral(lit);
                stats.enumLiteralsCreated++;
                annotateId(el, "EnumLiteral:" + t.qualifiedName + "#" + lit);
            }
        }

        // Fields -> Properties
        List<JField> fields = new ArrayList<>(t.fields);
        fields.sort((a, b) -> {
            String an = a == null ? "" : (a.name == null ? "" : a.name);
            String bn = b == null ? "" : (b.name == null ? "" : b.name);
            int c = an.compareTo(bn);
            if (c != 0) return c;
            String at = a == null ? "" : (a.type == null ? "" : a.type);
            String bt = b == null ? "" : (b.type == null ? "" : b.type);
            return at.compareTo(bt);
        });

        for (JField f : fields) {
            if (classifier instanceof StructuredClassifier) {
                Type umlType = resolveUmlType(classifier.getModel(), f.type);
                Property p;
                if (classifier instanceof Class) {
                    p = ((Class) classifier).createOwnedAttribute(f.name, umlType);
                } else if (classifier instanceof Interface) {
                    p = ((Interface) classifier).createOwnedAttribute(f.name, umlType);
                } else {
                    // Enumeration or other - skip fields
                    continue;
                }
                stats.attributesCreated++;
                annotateId(p, "Field:" + t.qualifiedName + "#" + f.name + ":" + f.type);
                annotateJavaTypeIfGeneric(p, f.type);
                setVisibility(p, f.visibility);
                if (f.isStatic) p.setIsStatic(true);
                if (f.isFinal) p.setIsReadOnly(true);
            }
        }

        // Methods -> Operations
        List<JMethod> methods = new ArrayList<>(t.methods);
        methods.sort((a, b) -> signatureKey(a).compareTo(signatureKey(b)));
        for (JMethod m : methods) {
            if (!(classifier instanceof org.eclipse.uml2.uml.Classifier)) continue;
            Operation op;
            if (classifier instanceof Class) {
                op = ((Class) classifier).createOwnedOperation(m.name, null, null);
            } else if (classifier instanceof Interface) {
                op = ((Interface) classifier).createOwnedOperation(m.name, null, null);
            } else {
                continue;
            }
            stats.operationsCreated++;
            annotateId(op, "Method:" + t.qualifiedName + "#" + signatureKey(m));
            setVisibility(op, m.visibility);
            op.setIsStatic(m.isStatic);
            if (m.isAbstract) op.setIsAbstract(true);

            // Parameters
            for (JParam p : m.params) {
                Type umlType = resolveUmlType(classifier.getModel(), p.type);
                Parameter umlParam = op.createOwnedParameter(p.name, umlType);
                stats.parametersCreated++;
                annotateId(umlParam, "Param:" + t.qualifiedName + "#" + signatureKey(m) + "/" + p.name + ":" + p.type);
                annotateJavaTypeIfGeneric(umlParam, p.type);
            }

            // Return
            if (!m.isConstructor) {
                Type ret = resolveUmlType(classifier.getModel(), m.returnType);
                Parameter retParam = op.createOwnedParameter("return", ret);
                retParam.setDirection(ParameterDirectionKind.RETURN_LITERAL);
                stats.parametersCreated++;
                annotateId(retParam, "Return:" + t.qualifiedName + "#" + signatureKey(m) + ":" + m.returnType);
                annotateJavaTypeIfGeneric(retParam, m.returnType);
            }
        }
    }

    private void addInheritanceAndRealization(Classifier classifier, JType t) {
        // extends
        if (t.extendsType != null && !t.extendsType.isBlank()) {
            Classifier superType = classifierByQName.get(t.extendsType);
            if (superType != null && classifier instanceof org.eclipse.uml2.uml.Class) {
                Generalization g = ((org.eclipse.uml2.uml.Class) classifier).createGeneralization(superType);
                stats.generalizationsCreated++;
                annotateId(g, "Generalization:" + t.qualifiedName + "->" + t.extendsType);
            }
        }

        // implements
        if (t.implementsTypes != null) {
            List<String> impl = new ArrayList<>(t.implementsTypes);
            impl.sort(String::compareTo);
            for (String ifaceName : impl) {
                Classifier iface = classifierByQName.get(ifaceName);
                if (iface instanceof Interface && classifier instanceof org.eclipse.uml2.uml.Class) {
                    InterfaceRealization ir = ((org.eclipse.uml2.uml.Class) classifier)
                            .createInterfaceRealization("realizes_" + ((Interface) iface).getName(), (Interface) iface);
                    stats.interfaceRealizationsCreated++;
                    annotateId(ir, "InterfaceRealization:" + t.qualifiedName + "->" + ifaceName);
                } else if (iface instanceof Interface && classifier instanceof Interface) {
                    // interface extends interface -> generalization
                    Generalization g = ((Interface) classifier).createGeneralization(iface);
                    stats.generalizationsCreated++;
                    annotateId(g, "InterfaceGeneralization:" + t.qualifiedName + "->" + ifaceName);
                }
            }
        }
    }

    private void addStructuralRelations(Classifier classifier, JType t) {
        // Very conservative Step-4 associations/dependencies:
        // - If a field references an in-model classifier (resolved), create an Association.
        // - For method signature references, create a Dependency.
        //
        // Later steps can refine multiplicities and association heuristics.        // Field-based associations
        for (JField f : t.fields) {
            AssociationTarget at = computeAssociationTarget(f);
            if (at == null) continue;

            Classifier target = resolveLocalClassifier(at.targetRef);
            if (target == null) continue;
            if (target == classifier) continue;
            if (!(classifier instanceof StructuredClassifier) || !(target instanceof Type)) continue;

            // Reuse the owned attribute created in addFeatures() as the navigable association end.
            StructuredClassifier sc = (StructuredClassifier) classifier;
            Property endToTarget = findOwnedAttribute(sc, f.name);
            if (endToTarget == null) {
                // Fallback (should be rare): create a property so the association has a named navigable end.
                endToTarget = sc.createOwnedAttribute(f.name, (Type) target);
                annotateId(endToTarget, "Field:" + t.qualifiedName + "#" + f.name + ":" + f.type);
                setVisibility(endToTarget, f.visibility);
            }

            // Ensure correct typing + multiplicity on the field-end.
            endToTarget.setType((Type) target);
            endToTarget.setLower(at.lower);
            endToTarget.setUpper(at.upper);
            endToTarget.setAggregation(AggregationKind.NONE_LITERAL);
            setVisibility(endToTarget, f.visibility);

            // Create an association owned by the source type's package (deterministic and tool-friendly).
            Package ownerPkg = ((Type) classifier).getPackage();
            if (ownerPkg == null) ownerPkg = classifier.getModel();

            Association assoc = UMLFactory.eINSTANCE.createAssociation();
            assoc.setName(null);
            ownerPkg.getPackagedElements().add(assoc);

            // Opposite end: unnamed, non-navigable by default (keeps diagrams clean).
            Property endToSource = assoc.createOwnedEnd(null, (Type) classifier);
            endToSource.setLower(0);
            endToSource.setUpper(1);
            endToSource.setAggregation(AggregationKind.NONE_LITERAL);

            // Attach the existing field property as the other end of the association.
            // IMPORTANT: keep the field Property owned by the Class (ownedAttribute).
            // Setting owningAssociation/association would re-home the Property under the Association and
            // remove it from the class' ownedAttributes. Instead, reference it from memberEnds only.
            if (!assoc.getMemberEnds().contains(endToTarget)) assoc.getMemberEnds().add(endToTarget);
            if (!assoc.getMemberEnds().contains(endToSource)) assoc.getMemberEnds().add(endToSource);

            // Navigability: UML tools generally treat classifier-owned member ends as navigable.
            // Avoid adding endToTarget to navigableOwnedEnds because that list is for association-owned ends
            // and may re-parent the Property.

            stats.associationsCreated++;
            annotateId(assoc, "Association:" + t.qualifiedName + "#" + f.name + "->" + at.targetRef + multiplicityKey(at));
            annotateId(endToSource, "AssociationEnd:" + t.qualifiedName + "#" + f.name + "<-" + t.qualifiedName);
        }

        // Method dependencies
        Set<String> deps = new HashSet<>();
        for (JMethod m : t.methods) {
            if (!m.isConstructor && m.returnType != null) {
                deps.add(stripGenerics(m.returnType));
            }
            for (JParam p : m.params) {
                deps.add(stripGenerics(p.type));
            }
        }

        List<String> sorted = new ArrayList<>(deps);
        Collections.sort(sorted);
        for (String depType : sorted) {
            Classifier target = classifierByQName.get(depType);
            if (target == null) continue;
            if (target == classifier) continue;
            Dependency d = classifier.createDependency((NamedElement) target);
            stats.dependenciesCreated++;
            annotateId(d, "Dependency:" + t.qualifiedName + "->" + depType);
        }
    }

    private Type resolveUmlType(Model model, String typeRef) {
    if (typeRef == null || typeRef.isBlank()) {
        return ensurePrimitive(model, "Object");
    }
    String base = stripGenerics(typeRef);
    if (base.endsWith("[]")) {
        base = base.substring(0, base.length() - 2);
    }

    // Local/project types (qualified names)
    Classifier inModel = classifierByQName.get(base);
    if (inModel instanceof Type) {
        return (Type) inModel;
    }

    // primitives
    switch (base) {
        case "boolean":
            return ensurePrimitive(model, "Boolean");
        case "byte":
        case "short":
        case "int":
        case "long":
            return ensurePrimitive(model, "Integer");
        case "float":
        case "double":
            return ensurePrimitive(model, "Real");
        case "char":
            return ensurePrimitive(model, "String");
        case "void":
            return ensurePrimitive(model, "void");
        default:
            // Common java.lang
            if ("String".equals(base) || "java.lang.String".equals(base)) {
                return ensurePrimitive(model, "String");
            }
            return ensureExternalStub(model, base);
    }
}

private Type ensureExternalStub(Model model, String qualifiedOrSimple) {
    String qn = qualifiedOrSimple == null ? "Object" : qualifiedOrSimple;
    String base = stripGenerics(qn);
    if (base.endsWith("[]")) base = base.substring(0, base.length() - 2);

    // Prefer java.lang.String as a PrimitiveType String
    if ("java.lang.String".equals(base) || "String".equals(base)) {
        return ensurePrimitive(model, "String");
    }

    // Boxed primitives / common types
    switch (base) {
        case "java.lang.Boolean":
        case "Boolean":
            return ensurePrimitive(model, "Boolean");
        case "java.lang.Integer":
        case "Integer":
        case "java.lang.Long":
        case "Long":
        case "java.lang.Short":
        case "Short":
        case "java.lang.Byte":
        case "Byte":
            return ensurePrimitive(model, "Integer");
        case "java.lang.Double":
        case "Double":
        case "java.lang.Float":
        case "Float":
            return ensurePrimitive(model, "Real");
        case "java.lang.Character":
        case "Character":
            return ensurePrimitive(model, "String");
    }

    String pkgName = "_external";
    String typeName = base;

    if (base.contains(".")) {
        int li = base.lastIndexOf('.');
        pkgName = "_external." + base.substring(0, li);
        typeName = base.substring(li + 1);
    }

    Package pkg = getOrCreatePackage(model, pkgName);
    Type existing = pkg.getOwnedType(typeName);
    if (existing != null) {
        return existing;
    }

    org.eclipse.uml2.uml.Class c = pkg.createOwnedClass(typeName, false);
    stats.externalStubsCreated++;
    annotateId(c, "ExternalStub:" + base);
    return c;
}

private PrimitiveType ensurePrimitive(Model model, String name) {
        // Put primitive types in a dedicated package to keep the model tidy.
        Package primitivesPkg = getOrCreatePackage(model, "_primitives");
        Type existing = primitivesPkg.getOwnedType(name);
        if (existing instanceof PrimitiveType) {
            return (PrimitiveType) existing;
        }
        PrimitiveType pt = primitivesPkg.createOwnedPrimitiveType(name);
        annotateId(pt, "Primitive:" + name);
        return pt;
    }



private Classifier resolveLocalClassifier(String possiblySimpleOrQualified) {
    if (possiblySimpleOrQualified == null || possiblySimpleOrQualified.isBlank()) return null;
    Classifier direct = classifierByQName.get(possiblySimpleOrQualified);
    if (direct != null) return direct;

    // If it's a simple name, try to find a unique qualified match.
    String name = possiblySimpleOrQualified;
    if (!name.contains(".")) {
        java.util.List<String> matches = new java.util.ArrayList<>();
        for (String qn : classifierByQName.keySet()) {
            if (qn.equals(name) || qn.endsWith("." + name)) {
                matches.add(qn);
            }
        }
        matches.sort(String::compareTo);
        if (!matches.isEmpty()) {
            return classifierByQName.get(matches.get(0));
        }
    }
    return null;
}

    private static final class AssociationTarget {
        final String targetRef;
        final int lower;
        final int upper;

        private AssociationTarget(String targetRef, int lower, int upper) {
            this.targetRef = targetRef;
            this.lower = lower;
            this.upper = upper;
        }
    }

    private static AssociationTarget computeAssociationTarget(JField f) {
        if (f == null || f.type == null || f.type.isBlank()) return null;
        String raw = f.type.trim();

        // Arrays
        if (raw.endsWith("[]")) {
            String base = raw.substring(0, raw.length() - 2).trim();
            base = stripGenerics(base);
            return new AssociationTarget(base, 0, -1);
        }

        String base = stripGenerics(raw);
        String simple = simpleName(base);

        // Optional<T> => 0..1 to T
        if (isOptionalLike(simple)) {
            String arg0 = firstGenericArg(raw);
            if (arg0 == null) return null;
            String target = stripArraySuffix(stripGenerics(arg0));
            return new AssociationTarget(target, 0, 1);
        }

        // Map<K,V> => 0..* to V (or K if only one arg is present)
        if (isMapLike(simple)) {
            String inner = genericInner(raw);
            if (inner == null) return null;
            java.util.List<String> args = splitTopLevelTypeArgs(inner);
            if (args.isEmpty()) return null;
            String chosen = args.size() >= 2 ? args.get(1) : args.get(0);
            String target = stripArraySuffix(stripGenerics(chosen));
            return new AssociationTarget(target, 0, -1);
        }

        // Collection-like containers (List/Set/Collection/Iterable) => 0..* to element type
        if (isCollectionLike(base)) {
            String arg0 = firstGenericArg(raw);
            if (arg0 == null) return null;
            String target = stripArraySuffix(stripGenerics(arg0));
            return new AssociationTarget(target, 0, -1);
        }

        // Default reference => 0..1 (more realistic than 1..1 for Java fields)
        String target = stripArraySuffix(stripGenerics(raw));
        return new AssociationTarget(target, 0, 1);
    }

    private static String multiplicityKey(AssociationTarget at) {
        if (at == null) return "";
        String up = at.upper < 0 ? "*" : Integer.toString(at.upper);
        return "[" + at.lower + ".." + up + "]";
    }

    private static Property findOwnedAttribute(StructuredClassifier sc, String name) {
        if (sc == null || name == null) return null;
        for (Property p : sc.getOwnedAttributes()) {
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    private static boolean isOptionalLike(String simpleName) {
        return "Optional".equals(simpleName);
    }

    private static boolean isMapLike(String simpleName) {
        return "Map".equals(simpleName);
    }

    private static String simpleName(String qualifiedOrSimple) {
        if (qualifiedOrSimple == null) return "";
        String s = qualifiedOrSimple.trim();
        if (s.endsWith("[]")) s = s.substring(0, s.length() - 2);
        int li = s.lastIndexOf('.');
        return li >= 0 ? s.substring(li + 1) : s;
    }

    private static String stripArraySuffix(String t) {
        if (t == null) return null;
        String s = t.trim();
        if (s.endsWith("[]")) return s.substring(0, s.length() - 2).trim();
        return s;
    }

    private static String genericInner(String javaTypeRef) {
        if (javaTypeRef == null) return null;
        String s = javaTypeRef.trim();
        int lt = s.indexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt < 0 || gt < lt) return null;
        String inner = s.substring(lt + 1, gt).trim();
        return inner.isEmpty() ? null : inner;
    }

private static boolean isCollectionLike(String baseType) {
    if (baseType == null) return false;
    String b = baseType;
    // normalize to simple name
    if (b.contains(".")) b = b.substring(b.lastIndexOf('.') + 1);
    return "List".equals(b) || "Set".equals(b) || "Collection".equals(b) || "Iterable".equals(b);
}

private static String firstGenericArg(String javaTypeRef) {
    if (javaTypeRef == null) return null;
    String s = javaTypeRef.trim();
    int lt = s.indexOf('<');
    int gt = s.lastIndexOf('>');
    if (lt < 0 || gt < lt) return null;
    String inner = s.substring(lt + 1, gt).trim();
    if (inner.isEmpty()) return null;
    // top-level split (no deep parsing needed here)
    int depth = 0;
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < inner.length(); i++) {
        char c = inner.charAt(i);
        if (c == '<') depth++;
        else if (c == '>') depth = Math.max(0, depth - 1);
        if (c == ',' && depth == 0) break;
        buf.append(c);
    }
    String arg = buf.toString().trim();
    return arg.isEmpty() ? null : arg;
}

    private static String stripGenerics(String t) {
        if (t == null) return "";
        int idx = t.indexOf('<');
        if (idx >= 0) {
            return t.substring(0, idx).trim();
        }
        return t.trim();
    }

    private static String signatureKey(JMethod m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.name).append('(');
        for (int i = 0; i < m.params.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(m.params.get(i).type);
        }
        sb.append(')');
        return sb.toString();
    }

    private static void setVisibility(NamedElement el, JVisibility v) {
        if (el == null || v == null) return;
        VisibilityKind k;
        switch (v) {
            case PRIVATE:
                k = VisibilityKind.PRIVATE_LITERAL;
                break;
            case PROTECTED:
                k = VisibilityKind.PROTECTED_LITERAL;
                break;
            case PACKAGE_PRIVATE:
                k = VisibilityKind.PACKAGE_LITERAL;
                break;
            case PUBLIC:
            default:
                k = VisibilityKind.PUBLIC_LITERAL;
                break;
        }
        el.setVisibility(k);
    }


private static void annotateJavaTypeIfGeneric(Element element, String javaTypeRef) {
    if (element == null || javaTypeRef == null) return;
    String s = javaTypeRef.trim();
    int lt = s.indexOf('<');
    int gt = s.lastIndexOf('>');
    if (lt < 0 || gt < lt) return;

    // Preserve full Java generic type string.
    addAnnotationValue(element, "java-to-xmi:javaType", s);

    // Best-effort extraction of top-level args (no deep parsing).
    String inner = s.substring(lt + 1, gt).trim();
    if (inner.isEmpty()) return;

    java.util.List<String> args = splitTopLevelTypeArgs(inner);
    if (!args.isEmpty()) {
        addAnnotationValue(element, "java-to-xmi:typeArgs", String.join(", ", args));
    }
}

private static java.util.List<String> splitTopLevelTypeArgs(String inner) {
    java.util.List<String> out = new java.util.ArrayList<>();
    StringBuilder buf = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < inner.length(); i++) {
        char c = inner.charAt(i);
        if (c == '<') depth++;
        else if (c == '>') depth = Math.max(0, depth - 1);

        if (c == ',' && depth == 0) {
            String part = buf.toString().trim();
            if (!part.isEmpty()) out.add(part);
            buf.setLength(0);
        } else {
            buf.append(c);
        }
    }
    String last = buf.toString().trim();
    if (!last.isEmpty()) out.add(last);
    return out;
}

private static void addAnnotationValue(Element element, String source, String value) {
    if (element == null || source == null || value == null) return;
    EAnnotation ann = element.getEAnnotation(source);
    if (ann == null) {
        ann = element.createEAnnotation(source);
    }
    ann.getDetails().put("value", value);
}

    private static void annotateId(Element element, String key) {
        if (element == null) return;
        String id = UmlIdStrategy.id(key);
        EAnnotation ann = element.getEAnnotation(ID_ANNOTATION_SOURCE);
        if (ann == null) {
            ann = element.createEAnnotation(ID_ANNOTATION_SOURCE);
        }
        ann.getDetails().put("id", id);
        ann.getDetails().put("key", key);
    }
}

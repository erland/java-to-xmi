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

    private final UmlBuildStats stats = new UmlBuildStats();

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

        return new Result(model, stats);
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
        Package owner = getOrCreatePackage(root, t.packageName);

        Classifier classifier;
        if (t.kind == JTypeKind.INTERFACE) {
            Interface i = owner.createOwnedInterface(t.name);
            classifier = i;
        } else if (t.kind == JTypeKind.ENUM) {
            Enumeration e = owner.createOwnedEnumeration(t.name);
            // We don't yet extract enum literals explicitly; could be added later.
            classifier = e;
        } else {
            // CLASS or ANNOTATION -> UML Class (annotation can be stereotyped later)
            Class c = owner.createOwnedClass(t.name, t.isAbstract);
            classifier = c;
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
        // Fields -> Properties
        for (JField f : t.fields) {
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
                setVisibility(p, f.visibility);
                if (f.isStatic) p.setIsStatic(true);
                if (f.isFinal) p.setIsReadOnly(true);
            }
        }

        // Methods -> Operations
        for (JMethod m : t.methods) {
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
            }

            // Return
            if (!m.isConstructor) {
                Type ret = resolveUmlType(classifier.getModel(), m.returnType);
                Parameter retParam = op.createOwnedParameter("return", ret);
                retParam.setDirection(ParameterDirectionKind.RETURN_LITERAL);
                stats.parametersCreated++;
                annotateId(retParam, "Return:" + t.qualifiedName + "#" + signatureKey(m) + ":" + m.returnType);
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
        // Later steps can refine multiplicities and association heuristics.

        // Field-based associations
        for (JField f : t.fields) {
            String ref = stripGenerics(f.type);
            Classifier target = classifierByQName.get(ref);
            if (target == null) continue;
            if (classifier == target) continue;
            if (!(classifier instanceof StructuredClassifier) || !(target instanceof Type)) continue;

            // Create an association owned by the nearest common package (we keep it simple: owner package of source)
            Package ownerPkg = ((Type) classifier).getPackage();
            if (ownerPkg == null) ownerPkg = classifier.getModel();

            Association assoc = UMLFactory.eINSTANCE.createAssociation();
            assoc.setName(null);
            ownerPkg.getPackagedElements().add(assoc);

            // End representing the field on the source classifier pointing to target
            Property endToTarget = assoc.createOwnedEnd(f.name, (Type) target);
            endToTarget.setLower(1);
            endToTarget.setUpper(1);
            endToTarget.setAggregation(AggregationKind.NONE_LITERAL);

            // Opposite end (unnamed or derived from classifier name)
            String backName = classifier.getName() == null ? "source" : classifier.getName().toLowerCase();
            Property endToSource = assoc.createOwnedEnd(backName, (Type) classifier);
            endToSource.setLower(0);
            endToSource.setUpper(1);
            endToSource.setAggregation(AggregationKind.NONE_LITERAL);

            stats.associationsCreated++;
            annotateId(assoc, "Association:" + t.qualifiedName + "#" + f.name + "->" + ref);
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

    private Type resolveUmlType(Model model, String javaTypeName) {
        // Very minimal type mapping.
        // - If the type is an in-model classifier, return it.
        // - Otherwise map primitives/common to UML PrimitiveTypes, or create a placeholder PrimitiveType.

        if (javaTypeName == null || javaTypeName.isBlank()) {
            return ensurePrimitive(model, "void");
        }

        String base = stripGenerics(javaTypeName);
        // arrays
        if (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
        }

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
                // common java.lang
                if ("String".equals(base) || "java.lang.String".equals(base)) {
                    return ensurePrimitive(model, "String");
                }
                return ensurePrimitive(model, base);
        }
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

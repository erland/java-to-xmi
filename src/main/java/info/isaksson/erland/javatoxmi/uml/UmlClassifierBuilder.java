package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.JTypeKind;

/**
 * Step 1/2: Create packages + classifiers and resolve basic UML types.
 */
final class UmlClassifierBuilder {

    private final ExternalTypeRegistry externalTypeRegistry = new ExternalTypeRegistry();

    Package getOrCreatePackage(UmlBuildContext ctx, String javaPackageName) {
        Model root = ctx.model;
        // Empty package maps to root model.
        if (javaPackageName == null || javaPackageName.isBlank()) {
            return root;
        }

        if (ctx.packageByName.containsKey(javaPackageName)) {
            return ctx.packageByName.get(javaPackageName);
        }

        String[] parts = javaPackageName.split("\\.");
        Package current = root;
        StringBuilder soFar = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) soFar.append('.');
            soFar.append(parts[i]);
            String q = soFar.toString();
            Package existing = ctx.packageByName.get(q);
            if (existing != null) {
                current = existing;
                continue;
            }
            Package created = current.createNestedPackage(parts[i]);
            ctx.stats.packagesCreated++;
            UmlBuilderSupport.annotateId(created, "Package:" + q);
            ctx.packageByName.put(q, created);
            current = created;
        }

        // Cache full name
        ctx.packageByName.put(javaPackageName, current);
        return current;
    }

    void createClassifier(UmlBuildContext ctx, JType t) {
        // Owner can be either a Package (top-level) or an enclosing Classifier (nested member type).
        Classifier enclosing = null;
        if (ctx.nestedTypesMode != NestedTypesMode.FLATTEN) {
            enclosing = (t.outerQualifiedName == null) ? null : ctx.classifierByQName.get(t.outerQualifiedName);
        }

        // IMPORTANT:
        // In Eclipse UML2 3.1 (2010), many "owned member" lists are derived/unmodifiable.
        // For nesting, attach via the real EMF containment feature "nestedClassifier" when present.
        Classifier classifier;
        if (enclosing != null) {
            classifier = createClassifierInstance(t);
            boolean attached = attachAsNested(enclosing, classifier);
            if (!attached) {
                // Defensive fallback: if the enclosing metaclass can't own nested classifiers in this
                // UML2 version, keep the model buildable by placing it at package level.
                Package owner = getOrCreatePackage(ctx, t.packageName);
                if (t.kind == JTypeKind.INTERFACE) {
                    classifier = owner.createOwnedInterface(t.name);
                } else if (t.kind == JTypeKind.ENUM) {
                    classifier = owner.createOwnedEnumeration(t.name);
                } else {
                    classifier = owner.createOwnedClass(t.name, t.isAbstract);
                }
            }
        } else {
            Package owner = getOrCreatePackage(ctx, t.packageName);
            if (t.kind == JTypeKind.INTERFACE) {
                classifier = owner.createOwnedInterface(t.name);
            } else if (t.kind == JTypeKind.ENUM) {
                classifier = owner.createOwnedEnumeration(t.name);
            } else {
                classifier = owner.createOwnedClass(t.name, t.isAbstract);
            }
        }

        ctx.stats.classifiersCreated++;
        UmlBuilderSupport.annotateId(classifier, "Classifier:" + t.qualifiedName);
        ctx.classifierByQName.put(t.qualifiedName, classifier);
        ctx.qNameByClassifier.put(classifier, t.qualifiedName);

        // Type-level JavaDoc -> UML owned comment (owned by the element)
        if (t.doc != null && !t.doc.isBlank() && classifier instanceof NamedElement) {
            Comment c = UMLFactory.eINSTANCE.createComment();
            c.setBody(t.doc);
            ((NamedElement) classifier).getOwnedComments().add(c);
            UmlBuilderSupport.annotateId(c, "Comment:" + t.qualifiedName);
            ctx.stats.commentsCreated++;
        }

        // Visibility
        if (classifier instanceof NamedElement) {
            UmlBuilderSupport.setVisibility((NamedElement) classifier, t.visibility);
        }
    }

    /**
     * Create a classifier instance (without assuming containment).
     * Containment is established separately (package-owned vs nested-owned).
     */
    private static Classifier createClassifierInstance(JType t) {
        if (t.kind == JTypeKind.INTERFACE) {
            Interface i = UMLFactory.eINSTANCE.createInterface();
            i.setName(t.name);
            return i;
        }
        if (t.kind == JTypeKind.ENUM) {
            Enumeration e = UMLFactory.eINSTANCE.createEnumeration();
            e.setName(t.name);
            return e;
        }

        Class c = UMLFactory.eINSTANCE.createClass();
        c.setName(t.name);
        c.setIsAbstract(t.isAbstract);
        return c;
    }

    /**
     * Attach {@code nested} as a nested classifier owned by {@code enclosing}.
     *
     * Eclipse UML2 3.1 implements several membership features as derived unions. Adding to those ELists
     * can throw {@link UnsupportedOperationException}. The safest available approach without relying on
     * newer helper APIs is to add via the underlying EMF structural feature when it exists.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean attachAsNested(Classifier enclosing, Classifier nested) {
        if (!(enclosing instanceof EObject) || !(nested instanceof EObject)) {
            return false;
        }
        EObject enc = (EObject) enclosing;
        EObject child = (EObject) nested;

        // Prefer the direct containment reference on Classifier: nestedClassifier
        EStructuralFeature f = enc.eClass().getEStructuralFeature("nestedClassifier");
        if (f != null) {
            try {
                Object v = enc.eGet(f);
                if (v instanceof EList) {
                    ((EList) v).add(child);
                    return true;
                }
            } catch (UnsupportedOperationException ex) {
                // fall through
            }
        }

        // Secondary attempt: ownedMember (sometimes concrete/containment depending on metaclass)
        f = enc.eClass().getEStructuralFeature("ownedMember");
        if (f != null) {
            try {
                Object v = enc.eGet(f);
                if (v instanceof EList) {
                    ((EList) v).add(child);
                    return true;
                }
            } catch (UnsupportedOperationException ex) {
                // ignore
            }
        }

        return false;
    }

    Type resolveUmlType(UmlBuildContext ctx, String typeRef) {
        Model model = ctx.model;
        if (typeRef == null || typeRef.isBlank()) {
            return ensurePrimitive(ctx, "Object");
        }
        String base = UmlAssociationBuilder.stripGenerics(typeRef);
        if (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
        }

        // Local/project types (qualified names)
        Classifier inModel = ctx.classifierByQName.get(base);
        if (inModel instanceof Type) {
            return (Type) inModel;
        }

        // primitives
        switch (base) {
            case "boolean" -> {
                return ensurePrimitive(ctx, "Boolean");
            }
            case "byte", "short", "int", "long" -> {
                return ensurePrimitive(ctx, "Integer");
            }
            case "float", "double" -> {
                return ensurePrimitive(ctx, "Real");
            }
            case "char" -> {
                return ensurePrimitive(ctx, "String");
            }
            case "void" -> {
                return ensurePrimitive(ctx, "void");
            }
            default -> {
                if ("String".equals(base) || "java.lang.String".equals(base)) {
                    return ensurePrimitive(ctx, "String");
                }

                // Boxed primitives / common Java types
                switch (base) {
                    case "java.lang.Boolean", "Boolean" -> {
                        return ensurePrimitive(ctx, "Boolean");
                    }
                    case "java.lang.Integer", "Integer", "java.lang.Long", "Long", "java.lang.Short", "Short", "java.lang.Byte", "Byte" -> {
                        return ensurePrimitive(ctx, "Integer");
                    }
                    case "java.lang.Double", "Double", "java.lang.Float", "Float" -> {
                        return ensurePrimitive(ctx, "Real");
                    }
                    case "java.lang.Character", "Character" -> {
                        return ensurePrimitive(ctx, "String");
                    }
                }

                return externalTypeRegistry.ensureExternalStub(ctx, base);
            }
        }
    }

    private PrimitiveType ensurePrimitive(UmlBuildContext ctx, String name) {
        Package primitivesPkg = getOrCreatePackage(ctx, "_primitives");
        Type existing = primitivesPkg.getOwnedType(name);
        if (existing instanceof PrimitiveType) {
            return (PrimitiveType) existing;
        }
        PrimitiveType pt = primitivesPkg.createOwnedPrimitiveType(name);
        UmlBuilderSupport.annotateId(pt, "Primitive:" + name);
        return pt;
    }
}

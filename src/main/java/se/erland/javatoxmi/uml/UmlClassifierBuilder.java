package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;

import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.JTypeKind;

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
        if (t.outerQualifiedName != null) {
            enclosing = ctx.classifierByQName.get(t.outerQualifiedName);
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
            Package owner = getOrCreatePackage(ctx, t.packageName);
            owner.getOwnedTypes().add(classifier);
        }

        ctx.stats.classifiersCreated++;
        UmlBuilderSupport.annotateId(classifier, "Classifier:" + t.qualifiedName);
        ctx.classifierByQName.put(t.qualifiedName, classifier);

        // Visibility
        if (classifier instanceof NamedElement) {
            UmlBuilderSupport.setVisibility((NamedElement) classifier, t.visibility);
        }
    }

    Type resolveUmlType(UmlBuildContext ctx, String typeRef) {
        Model model = ctx.model;
        if (typeRef == null || typeRef.isBlank()) {
            return ensurePrimitive(ctx, "Object");
        }
        String base = UmlRelationBuilder.stripGenerics(typeRef);
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

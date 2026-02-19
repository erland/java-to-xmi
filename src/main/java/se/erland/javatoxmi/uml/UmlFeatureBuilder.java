package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.StructuredClassifier;
import org.eclipse.uml2.uml.Type;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 3: Add attributes + operations + parameters.
 */
final class UmlFeatureBuilder {
    private final UmlClassifierBuilder classifierBuilder;

    UmlFeatureBuilder(UmlClassifierBuilder classifierBuilder) {
        this.classifierBuilder = classifierBuilder;
    }

    void addFeatures(UmlBuildContext ctx, Classifier classifier, JType t) {
        // Enum literals
        if (classifier instanceof Enumeration) {
            Enumeration e = (Enumeration) classifier;
            for (String lit : t.enumLiterals) {
                if (lit == null || lit.isBlank()) continue;
                EnumerationLiteral el = e.createOwnedLiteral(lit);
                ctx.stats.enumLiteralsCreated++;
                UmlBuilderSupport.annotateId(el, "EnumLiteral:" + t.qualifiedName + "#" + lit);
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
            if (!(classifier instanceof StructuredClassifier)) continue;

            Type umlType = classifierBuilder.resolveUmlType(ctx, f.type);
            Property p;
            if (classifier instanceof Class) {
                p = ((Class) classifier).createOwnedAttribute(f.name, umlType);
            } else if (classifier instanceof Interface) {
                p = ((Interface) classifier).createOwnedAttribute(f.name, umlType);
            } else {
                continue;
            }
            ctx.stats.attributesCreated++;
            UmlBuilderSupport.annotateId(p, "Field:" + t.qualifiedName + "#" + f.name + ":" + f.type);
            UmlBuilderSupport.annotateJavaTypeIfGeneric(p, f.type);
            UmlBuilderSupport.setVisibility(p, f.visibility);
            if (f.isStatic) p.setIsStatic(true);
            if (f.isFinal) p.setIsReadOnly(true);

            // Multiplicity + element/container tagging (arrays/collections/Optional + validation/JPA hints)
            MultiplicityResolver.Result mr = ctx.multiplicityResolver.resolve(f.typeRef, f.annotations);
            p.setLower(mr.lower);
            p.setUpper(mr.upper == MultiplicityResolver.STAR ? -1 : mr.upper);
            UmlBuilderSupport.annotateTags(p, mr.tags);
        }

        // Methods -> Operations
        Set<String> fieldNames = new HashSet<>();
        for (JField f : t.fields) {
            if (f != null && f.name != null && !f.name.isBlank()) {
                fieldNames.add(f.name);
            }
        }

        List<JMethod> methods = new ArrayList<>(t.methods);
        methods.sort((a, b) -> UmlBuilderSupport.signatureKey(a).compareTo(UmlBuilderSupport.signatureKey(b)));
        for (JMethod m : methods) {
            if (!ctx.includeConstructors && m.isConstructor) {
                continue;
            }
            if (!ctx.includeAccessors && isAccessorForExistingField(m, fieldNames)) {
                continue;
            }

            Operation op;
            if (classifier instanceof Class) {
                op = ((Class) classifier).createOwnedOperation(m.name, null, null);
            } else if (classifier instanceof Interface) {
                op = ((Interface) classifier).createOwnedOperation(m.name, null, null);
            } else {
                continue;
            }
            ctx.stats.operationsCreated++;
            UmlBuilderSupport.annotateId(op, "Method:" + t.qualifiedName + "#" + UmlBuilderSupport.signatureKey(m));
            UmlBuilderSupport.setVisibility(op, m.visibility);
            op.setIsStatic(m.isStatic);
            if (m.isAbstract) op.setIsAbstract(true);

            // Parameters
            for (JParam p : m.params) {
                Type pt = classifierBuilder.resolveUmlType(ctx, p.type);
                Parameter umlParam = op.createOwnedParameter(p.name, pt);
                ctx.stats.parametersCreated++;
                UmlBuilderSupport.annotateId(umlParam, "Param:" + t.qualifiedName + "#" + UmlBuilderSupport.signatureKey(m) + "/" + p.name + ":" + p.type);
                UmlBuilderSupport.annotateJavaTypeIfGeneric(umlParam, p.type);
            }

            // Return
            if (!m.isConstructor) {
                Type ret = classifierBuilder.resolveUmlType(ctx, m.returnType);
                Parameter retParam = op.createOwnedParameter("return", ret);
                retParam.setDirection(ParameterDirectionKind.RETURN_LITERAL);
                ctx.stats.parametersCreated++;
                UmlBuilderSupport.annotateId(retParam, "Return:" + t.qualifiedName + "#" + UmlBuilderSupport.signatureKey(m) + ":" + m.returnType);
                UmlBuilderSupport.annotateJavaTypeIfGeneric(retParam, m.returnType);
            }
        }
    }

    private static boolean isAccessorForExistingField(JMethod m, Set<String> fieldNames) {
        if (m == null || m.name == null) return false;
        if (fieldNames == null || fieldNames.isEmpty()) return false;
        if (m.isConstructor) return false;

        String n = m.name;
        // Getter: getX() : T
        if (n.startsWith("get") && n.length() > 3 && (m.params == null || m.params.isEmpty())) {
            String field = decapitalize(n.substring(3));
            return fieldNames.contains(field);
        }
        // Boolean getter: isX() : boolean/Boolean
        if (n.startsWith("is") && n.length() > 2 && (m.params == null || m.params.isEmpty())) {
            String rt = m.returnType == null ? "" : m.returnType.trim();
            if (rt.equals("boolean") || rt.equals("java.lang.Boolean") || rt.equals("Boolean")) {
                String field = decapitalize(n.substring(2));
                return fieldNames.contains(field);
            }
        }
        // Setter: setX(T)
        if (n.startsWith("set") && n.length() > 3 && m.params != null && m.params.size() == 1) {
            String field = decapitalize(n.substring(3));
            return fieldNames.contains(field);
        }
        return false;
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toLowerCase(Locale.ROOT);
        char c0 = s.charAt(0);
        char c1 = s.charAt(1);
        // Keep acronyms: URL -> URL
        if (Character.isUpperCase(c0) && Character.isUpperCase(c1)) return s;
        return Character.toLowerCase(c0) + s.substring(1);
    }
}

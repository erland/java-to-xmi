package info.isaksson.erland.javatoxmi.bridge;

import info.isaksson.erland.javatoxmi.ir.*;
import info.isaksson.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Best-effort bridge that exports the Java extractor model (JModel/JType/...) into the cross-language IR.
 *
 * <p>This is intentionally conservative and minimal. It is meant to enable the CLI flag
 * {@code --write-ir} so external extractors (e.g. Node/TS) can share a common IR and emitter pipeline.</p>
 */
public final class JModelToIrAdapter {

    private static final Set<String> PRIMITIVES = Set.of(
            "void", "boolean", "byte", "short", "int", "long", "char", "float", "double"
    );

    public IrModel toIr(JModel jm) {
        if (jm == null) return new IrModel("1.0", List.of(), List.of(), List.of(), List.of());

        List<IrClassifier> classifiers = new ArrayList<>();
        List<IrRelation> relations = new ArrayList<>();

        // Index types by qualified name
        Map<String, JType> byQn = new HashMap<>();
        for (JType t : jm.types) {
            if (t != null && t.qualifiedName != null && !t.qualifiedName.isBlank()) {
                byQn.put(t.qualifiedName, t);
            }
        }

        for (JType t : jm.types) {
            if (t == null) continue;
            classifiers.add(toClassifier(t));
        }

        // Inheritance + realization
        for (JType t : jm.types) {
            if (t == null) continue;
            String srcId = classifierId(t.qualifiedName);
            if (t.extendsType != null && !t.extendsType.isBlank()) {
                relations.add(new IrRelation(
                        "r:extends:" + t.qualifiedName + "->" + t.extendsType,
                        IrRelationKind.GENERALIZATION,
                        srcId,
                        classifierId(t.extendsType),
                        null,
                        List.of(),
                        List.of(),
                        null
                ));
            }
            if (t.implementsTypes != null) {
                for (String it : t.implementsTypes) {
                    if (it == null || it.isBlank()) continue;
                    relations.add(new IrRelation(
                            "r:impl:" + t.qualifiedName + "->" + it,
                            IrRelationKind.REALIZATION,
                            srcId,
                            classifierId(it),
                            null,
                            List.of(),
                            List.of(),
                            null
                    ));
                }
            }
        }

        // Field associations (best effort): if a field's raw type points to another project type
        for (JType t : jm.types) {
            if (t == null || t.fields == null) continue;
            String srcId = classifierId(t.qualifiedName);
            for (JField f : t.fields) {
                if (f == null) continue;
                String qn = guessQualifiedTypeName(f.type, byQn);
                if (qn == null) continue;
                relations.add(new IrRelation(
                        "r:assoc:" + t.qualifiedName + "." + f.name + "->" + qn,
                        IrRelationKind.ASSOCIATION,
                        srcId,
                        classifierId(qn),
                        f.name,
                        List.of(),
                        List.of(new IrTaggedValue("origin", "field")),
                        null
                ));
            }
        }

        // Dependency edges (from conservative method body deps list)
        for (JType t : jm.types) {
            if (t == null || t.methodBodyTypeDependencies == null) continue;
            String srcId = classifierId(t.qualifiedName);
            for (String dep : t.methodBodyTypeDependencies) {
                if (dep == null || dep.isBlank()) continue;
                relations.add(new IrRelation(
                        "r:dep:" + t.qualifiedName + "->" + dep,
                        IrRelationKind.DEPENDENCY,
                        srcId,
                        classifierId(dep),
                        null,
                        List.of(),
                        List.of(new IrTaggedValue("origin", "body")),
                        null
                ));
            }
        }

        return new IrModel("1.0", List.of(), classifiers, relations, List.of());
    }

    private IrClassifier toClassifier(JType t) {
        IrClassifierKind kind = switch (t.kind) {
            case CLASS -> IrClassifierKind.CLASS;
            case INTERFACE -> IrClassifierKind.INTERFACE;
            case ENUM -> IrClassifierKind.ENUM;
            case ANNOTATION -> IrClassifierKind.CLASS;
                    };

        List<IrStereotype> stereotypes = List.of();
        if (t.kind == JTypeKind.ANNOTATION) {
            stereotypes = List.of(new IrStereotype("Annotation", null));
        }


        List<IrAttribute> attrs = new ArrayList<>();
        if (t.fields != null) {
            for (JField f : t.fields) {
                if (f == null) continue;
                attrs.add(new IrAttribute(
                        "a:" + t.name + "." + f.name,
                        f.name,
                        toIrVisibility(f.visibility),
                        f.isStatic,
                        f.isFinal,
                        toIrTypeRef(f.type),
                        List.of(),
                        List.of(),
                        null
                ));
            }
        }

        List<IrOperation> ops = new ArrayList<>();
        if (t.methods != null) {
            for (JMethod m : t.methods) {
                if (m == null) continue;
                List<IrParameter> params = new ArrayList<>();
                if (m.params != null) {
                    for (JParam p : m.params) {
                        if (p == null) continue;
                        params.add(new IrParameter(p.name, toIrTypeRef(p.type), List.of()));
                    }
                }
                ops.add(new IrOperation(
                        "m:" + t.name + "." + m.name,
                        m.name,
                        toIrVisibility(m.visibility),
                        m.isStatic,
                        m.isAbstract,
                        m.isConstructor,
                        toIrTypeRef(m.returnType),
                        params,
                        List.of(),
                        List.of(),
                        null
                ));
            }
        }

        return new IrClassifier(
                classifierId(t.qualifiedName),
                t.name,
                t.qualifiedName,
                null,
                kind,
                toIrVisibility(t.visibility),
                attrs,
                ops,
                stereotypes,
                List.of(),
                null
        );
    }

    private static String classifierId(String qualifiedName) {
        String qn = qualifiedName == null ? "" : qualifiedName.trim();
        return "c:" + (qn.isEmpty() ? "UNKNOWN" : qn);
    }

    private static IrVisibility toIrVisibility(JVisibility v) {
        if (v == null) return IrVisibility.PACKAGE;
        return switch (v) {
            case PUBLIC -> IrVisibility.PUBLIC;
            case PROTECTED -> IrVisibility.PROTECTED;
            case PRIVATE -> IrVisibility.PRIVATE;
            case PACKAGE_PRIVATE -> IrVisibility.PACKAGE;
        };
    }

    private static IrTypeRef toIrTypeRef(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return new IrTypeRef(IrTypeRefKind.UNKNOWN, "java.lang.Object", List.of(), null, List.of());
        if (PRIMITIVES.contains(s)) return new IrTypeRef(IrTypeRefKind.PRIMITIVE, s, List.of(), null, List.of());
        return new IrTypeRef(IrTypeRefKind.NAMED, s, List.of(), null, List.of());
    }

    private static String guessQualifiedTypeName(String raw, Map<String, JType> byQn) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Quick normalize: strip generics and array brackets.
        s = s.replaceAll("<.*>$", "");
        s = s.replace("[]", "");

        // Exact qualified match
        if (byQn.containsKey(s)) return s;

        // Try by simple name
        for (String qn : byQn.keySet()) {
            int idx = qn.lastIndexOf('.');
            String sn = idx >= 0 ? qn.substring(idx + 1) : qn;
            if (sn.equals(s)) return qn;
        }
        return null;
    }
}

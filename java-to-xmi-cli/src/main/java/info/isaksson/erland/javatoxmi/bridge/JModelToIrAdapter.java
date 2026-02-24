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

        V2Stereotypes v2 = toV2Stereotypes(classifiers, relations);

        return new IrModel("1.0", v2.defs(), List.of(), v2.classifiers(), v2.relations(), List.of());
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


    private static final String DEFAULT_PROFILE = "java";

    /**
     * Populate IR schema v2 stereotype registry + refs from legacy v1 `stereotypes` arrays.
     *
     * <p>This keeps legacy stereotypes intact (for backward compatibility) but also emits:
     * - IrModel.stereotypeDefinitions (unique definitions, sorted by id)
     * - element.stereotypeRefs (sorted by stereotypeId)</p>
     */
    private static V2Stereotypes toV2Stereotypes(List<IrClassifier> classifiers, List<IrRelation> relations) {
        Map<String, IrStereotypeDefinition> defs = new HashMap<>();

        List<IrClassifier> outClassifiers = new ArrayList<>();
        for (IrClassifier c : classifiers) {
            List<IrStereotypeRef> refs = refsFor("classifier", c.kind, null, c.stereotypes, defs);
            outClassifiers.add(new IrClassifier(
                    c.id, c.name, c.qualifiedName, c.packageId, c.kind, c.visibility,
                    mapAttrs(c.attributes, defs),
                    mapOps(c.operations, defs),
                    c.stereotypes,
                    refs,
                    c.taggedValues,
                    c.source
            ));
        }

        List<IrRelation> outRelations = new ArrayList<>();
        for (IrRelation r : relations) {
            List<IrStereotypeRef> refs = refsForRelation(r.kind, r.stereotypes, defs);
            outRelations.add(new IrRelation(
                    r.id, r.kind, r.sourceId, r.targetId, r.name,
                    r.stereotypes,
                    refs,
                    r.taggedValues,
                    r.source
            ));
        }

        List<IrStereotypeDefinition> defList = new ArrayList<>(defs.values());
        defList.sort(Comparator.comparing(d -> d.id == null ? "" : d.id));

        return new V2Stereotypes(defList, outClassifiers, outRelations);
    }

    private static List<IrAttribute> mapAttrs(List<IrAttribute> attrs, Map<String, IrStereotypeDefinition> defs) {
        if (attrs == null || attrs.isEmpty()) return List.of();
        List<IrAttribute> out = new ArrayList<>();
        for (IrAttribute a : attrs) {
            List<IrStereotypeRef> refs = refsForAttribute(a.stereotypes, defs);
            out.add(new IrAttribute(
                    a.id, a.name, a.visibility, a.isStatic, a.isFinal, a.type,
                    a.stereotypes,
                    refs,
                    a.taggedValues,
                    a.source
            ));
        }
        return out;
    }

    private static List<IrOperation> mapOps(List<IrOperation> ops, Map<String, IrStereotypeDefinition> defs) {
        if (ops == null || ops.isEmpty()) return List.of();
        List<IrOperation> out = new ArrayList<>();
        for (IrOperation o : ops) {
            List<IrStereotypeRef> refs = refsForOperation(o.stereotypes, defs);
            out.add(new IrOperation(
                    o.id, o.name, o.visibility, o.isStatic, o.isAbstract, o.isConstructor,
                    o.returnType, o.parameters,
                    o.stereotypes,
                    refs,
                    o.taggedValues,
                    o.source
            ));
        }
        return out;
    }

    private static List<IrStereotypeRef> refsForAttribute(List<IrStereotype> stereotypes, Map<String, IrStereotypeDefinition> defs) {
        return refsForAppliesTo(List.of("Property"), stereotypes, defs);
    }

    private static List<IrStereotypeRef> refsForOperation(List<IrStereotype> stereotypes, Map<String, IrStereotypeDefinition> defs) {
        return refsForAppliesTo(List.of("Operation"), stereotypes, defs);
    }

    private static List<IrStereotypeRef> refsForRelation(IrRelationKind kind, List<IrStereotype> stereotypes, Map<String, IrStereotypeDefinition> defs) {
        List<String> appliesTo = switch (kind) {
            case GENERALIZATION -> List.of("Generalization");
            case REALIZATION -> List.of("InterfaceRealization");
            case ASSOCIATION, AGGREGATION, COMPOSITION -> List.of("Association");
            default -> List.of("Dependency");
        };
        return refsForAppliesTo(appliesTo, stereotypes, defs);
    }

    private static List<IrStereotypeRef> refsFor(String owner, IrClassifierKind classifierKind, IrRelationKind relationKind,
                                                 List<IrStereotype> stereotypes, Map<String, IrStereotypeDefinition> defs) {
        // Currently only classifier stereotypes are emitted by the adapter, but keep this generic.
        List<String> appliesTo;
        if ("classifier".equals(owner)) {
            appliesTo = switch (classifierKind) {
                case INTERFACE -> List.of("Interface");
                case ENUM -> List.of("Enumeration");
                default -> List.of("Class");
            };
        } else {
            appliesTo = List.of("NamedElement");
        }
        return refsForAppliesTo(appliesTo, stereotypes, defs);
    }

    private static List<IrStereotypeRef> refsForAppliesTo(List<String> appliesTo, List<IrStereotype> stereotypes,
                                                          Map<String, IrStereotypeDefinition> defs) {
        if (stereotypes == null || stereotypes.isEmpty()) return List.of();

        List<IrStereotypeRef> refs = new ArrayList<>();
        for (IrStereotype s : stereotypes) {
            if (s == null || s.name == null || s.name.isBlank()) continue;
            String id = stableStereoId(DEFAULT_PROFILE, s.name);
            defs.putIfAbsent(id, new IrStereotypeDefinition(
                    id,
                    s.name,
                    s.qualifiedName,
                    DEFAULT_PROFILE,
                    appliesTo,
                    List.of()
            ));
            refs.add(new IrStereotypeRef(id, Map.of()));
        }
        refs.sort(Comparator.comparing(r -> r.stereotypeId == null ? "" : r.stereotypeId));
        return refs;
    }

    private static String stableStereoId(String profile, String name) {
        String ns = profile == null ? "generic" : profile.trim().toLowerCase();
        if (ns.isEmpty()) ns = "generic";
        String local = name == null ? "Stereotype" : name.trim();
        if (local.isEmpty()) local = "Stereotype";
        // Keep local name case, but sanitize.
        local = local.replaceAll("[^A-Za-z0-9_.-]", "_");
        ns = ns.replaceAll("[^a-z0-9_.-]", "_");
        return "st:" + ns + "." + local;
    }

    private static final class V2Stereotypes {
        private final List<IrStereotypeDefinition> defs;
        private final List<IrClassifier> classifiers;
        private final List<IrRelation> relations;

        private V2Stereotypes(List<IrStereotypeDefinition> defs, List<IrClassifier> classifiers, List<IrRelation> relations) {
            this.defs = defs;
            this.classifiers = classifiers;
            this.relations = relations;
        }

        private List<IrStereotypeDefinition> defs() { return defs; }
        private List<IrClassifier> classifiers() { return classifiers; }
        private List<IrRelation> relations() { return relations; }
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

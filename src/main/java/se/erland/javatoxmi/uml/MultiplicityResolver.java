package se.erland.javatoxmi.uml;

import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.TypeRefKind;

import java.util.*;

/**
 * Best-effort multiplicity resolver for UML mapping.
 *
 * <p>Designed to be conservative and independent of symbol solving. It infers
 * multiplicity from structural type information (arrays/collections/Optional)
 * and then tightens bounds using validation and JPA annotations.</p>
 */
public final class MultiplicityResolver {

    /** Upper bound value representing '*' (unbounded). */
    public static final int STAR = -1;

    public static final class Result {
        public final int lower;
        public final int upper; // STAR for '*'
        public final Map<String, String> tags;

        public Result(int lower, int upper, Map<String, String> tags) {
            this.lower = Math.max(0, lower);
            this.upper = upper < 0 ? STAR : upper;
            this.tags = tags == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tags));
        }
    }

    private static final Set<String> PRIMITIVES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean", "char", "void"
    );

    private static final Set<String> COLLECTION_SIMPLE_NAMES = Set.of(
            "Collection", "List", "Set", "Iterable"
    );

    /** Resolve multiplicity from a type + annotations. */
    public Result resolve(TypeRef typeRef, List<JAnnotationUse> annotations) {
        List<JAnnotationUse> anns = annotations == null ? List.of() : annotations;

        // 1) Baseline from JPA relation annotations if present
        JpaInfo jpa = findJpaInfo(anns);

        int lower;
        int upper;
        Map<String, String> tags = new LinkedHashMap<>();

        if (jpa.relation != null) {
            tags.put("jpaRelation", jpa.relation);
            if (jpa.toMany) {
                lower = 0;
                upper = STAR;
            } else {
                // To-one
                lower = 0;
                upper = 1;
            }

            if (jpa.lowerIsOne) {
                lower = 1;
                tags.put("nullableSource", jpa.lowerSource);
            }
        } else {
            // 2) Baseline from structure (arrays/collections/Optional/primitive)
            StructureInfo s = analyzeStructure(typeRef);
            lower = s.lower;
            upper = s.upper;
            tags.putAll(s.tags);
        }

        // 3) Tighten with validation annotations
        ValidationInfo v = findValidationInfo(anns);
        if (v.lowerAtLeastOne) {
            lower = Math.max(lower, 1);
        }
        if (v.sizeMin != null) {
            lower = Math.max(lower, v.sizeMin);
        }
        if (v.sizeMax != null) {
            if (upper == STAR) {
                upper = v.sizeMax;
            } else {
                upper = Math.min(upper, v.sizeMax);
            }
        }
        if (v.sizeMin != null) tags.put("validationSizeMin", String.valueOf(v.sizeMin));
        if (v.sizeMax != null) tags.put("validationSizeMax", String.valueOf(v.sizeMax));

        return new Result(lower, upper, tags);
    }

    // --- structure inference -------------------------------------------------

    private static final class StructureInfo {
        final int lower;
        final int upper;
        final Map<String, String> tags;
        StructureInfo(int lower, int upper, Map<String, String> tags) {
            this.lower = lower;
            this.upper = upper;
            this.tags = tags;
        }
    }

    private StructureInfo analyzeStructure(TypeRef typeRef) {
        if (typeRef == null) {
            return new StructureInfo(0, 1, Map.of());
        }

        String raw = safe(typeRef.raw);
        String simple = safe(typeRef.simpleName);

        Map<String, String> tags = new LinkedHashMap<>();

        // Primitive: assume required
        if (typeRef.kind == TypeRefKind.SIMPLE && isPrimitiveName(simple, raw)) {
            return new StructureInfo(1, 1, tags);
        }

        // Arrays => 0..*
        if (typeRef.kind == TypeRefKind.ARRAY || raw.endsWith("[]")) {
            tags.put("isArray", "true");
            TypeRef elem = firstArg(typeRef);
            if (elem != null) {
                tags.put("elementType", bestTypeLabel(elem));
            }
            return new StructureInfo(0, STAR, tags);
        }

        // Optional<T> => 0..1
        if (isOptional(typeRef)) {
            tags.put("containerKind", "Optional");
            TypeRef elem = firstArg(typeRef);
            if (elem != null) tags.put("elementType", bestTypeLabel(elem));
            return new StructureInfo(0, 1, tags);
        }

        // Collections => 0..*
        if (isCollection(typeRef)) {
            tags.put("collectionKind", bestContainerKind(typeRef));
            TypeRef elem = firstArg(typeRef);
            if (elem != null) tags.put("elementType", bestTypeLabel(elem));
            return new StructureInfo(0, STAR, tags);
        }

        // Map<K,V> => treat as 0..* container (but multiplicity is arguably 0..1 as a property).
        // For UML association ends we usually want the "many" semantics, so default to 0..*.
        // Consumers can interpret tags.
        if (isMap(typeRef)) {
            tags.put("collectionKind", "Map");
            if (typeRef.args != null && typeRef.args.size() >= 2) {
                tags.put("mapKeyType", bestTypeLabel(typeRef.args.get(0)));
                tags.put("mapValueType", bestTypeLabel(typeRef.args.get(1)));
            }
            return new StructureInfo(0, STAR, tags);
        }

        // Default reference type
        return new StructureInfo(0, 1, tags);
    }

    private static boolean isPrimitiveName(String simple, String raw) {
        String s = !simple.isBlank() ? simple : raw;
        s = s.trim();
        return PRIMITIVES.contains(s);
    }

    private static boolean isOptional(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if ("Optional".equals(sn)) return true;
        return qn.endsWith("java.util.Optional") || safe(t.raw).startsWith("Optional<");
    }

    private static boolean isCollection(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if (COLLECTION_SIMPLE_NAMES.contains(sn)) return true;
        return qn.endsWith("java.util.Collection")
                || qn.endsWith("java.util.List")
                || qn.endsWith("java.util.Set")
                || qn.endsWith("java.lang.Iterable");
    }

    private static boolean isMap(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if ("Map".equals(sn)) return true;
        return qn.endsWith("java.util.Map") || safe(t.raw).startsWith("Map<");
    }

    private static String bestContainerKind(TypeRef t) {
        if (t == null) return "";
        String sn = safe(t.simpleName);
        if (!sn.isBlank()) return sn;
        String raw = safe(t.raw);
        int idx = raw.indexOf('<');
        String base = idx >= 0 ? raw.substring(0, idx) : raw;
        base = base.trim();
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    private static TypeRef firstArg(TypeRef t) {
        if (t == null || t.args == null || t.args.isEmpty()) return null;
        return t.args.get(0);
    }

    private static String bestTypeLabel(TypeRef t) {
        if (t == null) return "";
        if (!safe(t.qnameHint).isBlank()) return t.qnameHint;
        if (!safe(t.raw).isBlank()) return t.raw;
        return t.simpleName;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // --- validation annotations ---------------------------------------------

    private static final class ValidationInfo {
        boolean lowerAtLeastOne;
        Integer sizeMin;
        Integer sizeMax;
    }

    private ValidationInfo findValidationInfo(List<JAnnotationUse> anns) {
        ValidationInfo v = new ValidationInfo();
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "NotNull", "Nonnull")) {
                v.lowerAtLeastOne = true;
            }
            if (isAny(n, "NotEmpty", "NotBlank")) {
                v.lowerAtLeastOne = true;
            }
            if (isAny(n, "Size")) {
                Integer min = parseInt(a.values.get("min"));
                Integer max = parseInt(a.values.get("max"));
                if (min != null) v.sizeMin = min;
                if (max != null) v.sizeMax = max;
            }
        }
        return v;
    }

    // --- JPA annotations -----------------------------------------------------

    private static final class JpaInfo {
        String relation; // OneToMany etc
        boolean toMany;
        boolean lowerIsOne;
        String lowerSource;
    }

    private JpaInfo findJpaInfo(List<JAnnotationUse> anns) {
        JpaInfo j = new JpaInfo();

        // First: relationship type
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "OneToOne", "ManyToOne", "OneToMany", "ManyToMany")) {
                j.relation = stripPkg(n);
                j.toMany = isAny(n, "OneToMany", "ManyToMany");

                // optional=false on to-one relations
                if (!j.toMany) {
                    String opt = a.values.get("optional");
                    if (opt != null && "false".equalsIgnoreCase(opt.trim())) {
                        j.lowerIsOne = true;
                        j.lowerSource = j.relation + ".optional=false";
                    }
                }
            }
        }

        // Second: nullable=false sources (also for basic attributes)
        for (JAnnotationUse a : anns) {
            String n = annName(a);
            if (isAny(n, "JoinColumn", "Column")) {
                String nullable = a.values.get("nullable");
                if (nullable != null && "false".equalsIgnoreCase(nullable.trim())) {
                    j.lowerIsOne = true;
                    j.lowerSource = stripPkg(n) + ".nullable=false";
                }
            }
            if (isAny(n, "Basic")) {
                String optional = a.values.get("optional");
                if (optional != null && "false".equalsIgnoreCase(optional.trim())) {
                    j.lowerIsOne = true;
                    j.lowerSource = "Basic.optional=false";
                }
            }
        }

        return j;
    }

    // --- helpers ------------------------------------------------------------

    private static String annName(JAnnotationUse a) {
        if (a == null) return "";
        if (a.qualifiedName != null && !a.qualifiedName.isBlank()) return a.qualifiedName;
        return a.simpleName == null ? "" : a.simpleName;
    }

    private static String stripPkg(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static boolean isAny(String annName, String... simpleNames) {
        String s = stripPkg(annName);
        for (String n : simpleNames) {
            if (n.equals(s)) return true;
        }
        return false;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        // Some extractors may store quoted numbers
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        if (!t.matches("-?\\d+")) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

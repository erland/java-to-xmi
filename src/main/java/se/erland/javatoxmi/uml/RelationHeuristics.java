package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.TypeRefKind;

import java.util.Map;
import java.util.Set;

/**
 * Pure, testable heuristics for deciding whether a Java field should be represented
 * as an association vs attribute-only, and whether the association end should be
 * aggregation/composition.
 */
public final class RelationHeuristics {
    private RelationHeuristics() {}

    // JPA annotation names (simple + qualified) supported.
    private static final Set<String> JPA_ONE_TO_ONE = Set.of("OneToOne", "javax.persistence.OneToOne", "jakarta.persistence.OneToOne");
    private static final Set<String> JPA_ONE_TO_MANY = Set.of("OneToMany", "javax.persistence.OneToMany", "jakarta.persistence.OneToMany");
    private static final Set<String> JPA_MANY_TO_ONE = Set.of("ManyToOne", "javax.persistence.ManyToOne", "jakarta.persistence.ManyToOne");
    private static final Set<String> JPA_MANY_TO_MANY = Set.of("ManyToMany", "javax.persistence.ManyToMany", "jakarta.persistence.ManyToMany");
    private static final Set<String> JPA_EMBEDDED = Set.of("Embedded", "javax.persistence.Embedded", "jakarta.persistence.Embedded");
    private static final Set<String> JPA_EMBEDDED_ID = Set.of("EmbeddedId", "javax.persistence.EmbeddedId", "jakarta.persistence.EmbeddedId");
    private static final Set<String> JPA_ELEMENT_COLLECTION = Set.of("ElementCollection", "javax.persistence.ElementCollection", "jakarta.persistence.ElementCollection");
    private static final Set<String> JPA_TRANSIENT = Set.of("Transient", "javax.persistence.Transient", "jakarta.persistence.Transient");

    // Value-like types to keep as attributes-only in SMART mode (best-effort).
    private static final Set<String> VALUE_LIKE_SIMPLE = Set.of(
            "String",
            "UUID",
            "BigDecimal",
            "BigInteger",
            "LocalDate",
            "LocalDateTime",
            "Instant",
            "Date",
            "URI",
            "URL"
    );
    private static final Set<String> VALUE_LIKE_QNAME = Set.of(
            "java.lang.String",
            "java.util.UUID",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.Instant",
            "java.util.Date",
            "java.net.URI",
            "java.net.URL"
    );

    /**
     * Decide whether a field should be exported as an Association.
     *
     * @param fieldTypeResolvesToModelClassifier pass true when the field's (target) type is a classifier in the model
     */
    public static boolean shouldCreateAssociation(JField field,
                                                  JType owner,
                                                  AssociationPolicy policy,
                                                  boolean fieldTypeResolvesToModelClassifier) {
        if (policy == null) policy = AssociationPolicy.RESOLVED;
        if (policy == AssociationPolicy.NONE) return false;

        // Never create relationship lines for non-persistent fields.
        if (isTransient(field)) return false;

        // JPA embedded/element collection are persistence containment and should be represented
        // as composition relationships when the target resolves to an in-model classifier.
        // For element collections of basic/value-like types (e.g. List<String>) we keep attribute-only.
        if (isEmbedded(field) || isEmbeddedId(field)) {
            return fieldTypeResolvesToModelClassifier;
        }
        if (isElementCollection(field)) {
            return fieldTypeResolvesToModelClassifier && !isElementCollectionOfValueLike(field);
        }

        // JPA embedded/element collection are attribute-like containment, not association lines.
        if (isEmbedded(field) || isElementCollection(field)) return false;

        boolean hasJpaRelation = hasJpaRelationship(field);

        switch (policy) {
            case JPA_ONLY:
                return hasJpaRelation;
            case RESOLVED:
                return fieldTypeResolvesToModelClassifier;
            case SMART:
                if (hasJpaRelation) return true;
                if (isValueLike(field)) return false;
                return fieldTypeResolvesToModelClassifier;
            default:
                return fieldTypeResolvesToModelClassifier;
        }
    }

    /**
     * Determine AggregationKind for a field end.
     *
     * Currently conservative:
     * - COMPOSITE when orphanRemoval=true on OneToMany/OneToOne
     * - otherwise NONE
     */
    public static AggregationKind aggregationKindFor(JField field) {
        if (field == null) return AggregationKind.NONE_LITERAL;

        // Embedded objects are owned-by-value in JPA.
        if (isEmbedded(field) || isEmbeddedId(field)) {
            return AggregationKind.COMPOSITE_LITERAL;
        }

        // Element collections are contained; but avoid implying composition to basic/value-like types.
        if (isElementCollection(field) && !isElementCollectionOfValueLike(field)) {
            return AggregationKind.COMPOSITE_LITERAL;
        }
        JAnnotationUse oneToMany = findAnnotation(field, JPA_ONE_TO_MANY);
        JAnnotationUse oneToOne = findAnnotation(field, JPA_ONE_TO_ONE);
        JAnnotationUse rel = oneToMany != null ? oneToMany : oneToOne;
        if (rel != null && isTrue(rel.values, "orphanRemoval")) {
            return AggregationKind.COMPOSITE_LITERAL;
        }
        return AggregationKind.NONE_LITERAL;
    }

    public static boolean hasJpaRelationship(JField field) {
        return findAnnotation(field, JPA_ONE_TO_ONE) != null
                || findAnnotation(field, JPA_ONE_TO_MANY) != null
                || findAnnotation(field, JPA_MANY_TO_ONE) != null
                || findAnnotation(field, JPA_MANY_TO_MANY) != null;
    }

    public static boolean isEmbedded(JField field) {
        return findAnnotation(field, JPA_EMBEDDED) != null;
    }

    public static boolean isEmbeddedId(JField field) {
        return findAnnotation(field, JPA_EMBEDDED_ID) != null;
    }

    public static boolean isElementCollection(JField field) {
        return findAnnotation(field, JPA_ELEMENT_COLLECTION) != null;
    }

    public static boolean isTransient(JField field) {
        return findAnnotation(field, JPA_TRANSIENT) != null;
    }

    private static boolean isElementCollectionOfValueLike(JField field) {
        if (field == null) return false;
        // Best-effort: if the element type is value-like, don't imply composition.
        TypeRef tr = field.typeRef;
        if (tr != null) {
            TypeRef elem = null;
            if (tr.kind == TypeRefKind.PARAM && tr.args != null && !tr.args.isEmpty()) {
                elem = tr.args.get(0);
            }
            if (elem != null) {
                String qn = safe(elem.qnameHint);
                String sn = safe(elem.simpleName);
                if (!qn.isBlank() && VALUE_LIKE_QNAME.contains(qn)) return true;
                if (!sn.isBlank() && VALUE_LIKE_SIMPLE.contains(sn)) return true;
                if (isPrimitiveName(sn) || isPrimitiveName(qn)) return true;
            }
        }
        // Fallback: if the declared type string looks like List<String> etc.
        String raw = field.type == null ? "" : field.type.trim();
        if (raw.contains("<") && raw.contains(">")) {
            String inner = raw.substring(raw.indexOf('<') + 1, raw.lastIndexOf('>')).trim();
            // for Map<K,V> this is "K,V"; treat as non-value-like to be safe
            if (!inner.contains(",")) {
                String base = inner;
                if (VALUE_LIKE_QNAME.contains(base) || VALUE_LIKE_SIMPLE.contains(simpleName(base))) return true;
            }
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String simpleName(String qn) {
        if (qn == null) return "";
        String s = qn.trim();
        int i = s.lastIndexOf('.');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    /**
     * Best-effort classification of value-like types that should remain attributes-only.
     */
    public static boolean isValueLike(JField field) {
        if (field == null) return false;
        TypeRef tr = field.typeRef;
        if (tr != null) {
            // arrays/collections are not value-like for this purpose
            if (tr.kind == TypeRefKind.ARRAY || tr.kind == TypeRefKind.PARAM) return false;
            if (!tr.qnameHint.isBlank() && VALUE_LIKE_QNAME.contains(tr.qnameHint)) return true;
            if (!tr.simpleName.isBlank() && VALUE_LIKE_SIMPLE.contains(tr.simpleName)) return true;
        }
        // fallback to string
        String t = field.type == null ? "" : field.type.trim();
        if (VALUE_LIKE_QNAME.contains(t)) return true;
        return VALUE_LIKE_SIMPLE.contains(t) || isPrimitiveName(t);
    }

    private static boolean isPrimitiveName(String t) {
        return t.equals("byte") || t.equals("short") || t.equals("int") || t.equals("long")
                || t.equals("float") || t.equals("double") || t.equals("boolean") || t.equals("char");
    }

    private static boolean isTrue(Map<String, String> values, String key) {
        if (values == null) return false;
        String v = values.get(key);
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private static JAnnotationUse findAnnotation(JField field, Set<String> names) {
        if (field == null || field.annotations == null) return null;
        for (JAnnotationUse a : field.annotations) {
            if (a == null) continue;
            if (names.contains(a.qualifiedName)) return a;
            if (names.contains(a.simpleName)) return a;
        }
        return null;
    }
}

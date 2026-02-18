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
    private static final Set<String> JPA_ELEMENT_COLLECTION = Set.of("ElementCollection", "javax.persistence.ElementCollection", "jakarta.persistence.ElementCollection");

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

    public static boolean isElementCollection(JField field) {
        return findAnnotation(field, JPA_ELEMENT_COLLECTION) != null;
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

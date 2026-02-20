package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JField;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small, focused tag helpers for explaining why a relationship/aggregation was chosen.
 *
 * <p>Keeping this outside {@link UmlAssociationBuilder} avoids mixing rule-density
 * (policy decisions) with UML object mutation.</p>
 */
final class RelationTagging {
    private RelationTagging() {}

    static Map<String, String> relationDecisionTags(JField f, AssociationPolicy policy, boolean resolvedTarget) {
        if (f == null) return Map.of();
        if (policy == null) policy = AssociationPolicy.RESOLVED;

        if (RelationHeuristics.isTransient(f)) {
            return Map.of("relationSource", "transient", "persistent", "false");
        }

        if (RelationHeuristics.isEmbedded(f)) {
            return Map.of("relationSource", "embedded");
        }
        if (RelationHeuristics.isEmbeddedId(f)) {
            return Map.of("relationSource", "embeddedId");
        }
        if (RelationHeuristics.isElementCollection(f)) {
            return Map.of("relationSource", "elementCollection");
        }

        boolean hasJpa = RelationHeuristics.hasJpaRelationship(f);
        if (hasJpa) {
            return Map.of("relationSource", "jpa");
        }

        switch (policy) {
            case NONE:
                return Map.of("relationSource", "none");
            case JPA_ONLY:
                return Map.of("relationSource", "jpaOnly");
            case SMART:
                if (RelationHeuristics.isValueLike(f)) {
                    return Map.of("relationSource", "valueBlacklist");
                }
                if (resolvedTarget) {
                    return Map.of("relationSource", "resolved");
                }
                return Map.of("relationSource", "unresolved");
            case RESOLVED:
            default:
                return Map.of("relationSource", resolvedTarget ? "resolved" : "unresolved");
        }
    }

    static Map<String, String> aggregationDecisionTags(JField f) {
        if (f == null) return Map.of();
        AggregationKind ak = RelationHeuristics.aggregationKindFor(f);
        String agg = ak == AggregationKind.COMPOSITE_LITERAL ? "composite" : "none";
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("aggregation", agg);

        if (f.annotations != null) {
            for (JAnnotationUse a : f.annotations) {
                if (a == null) continue;
                if ("OneToMany".equals(a.simpleName) || "OneToOne".equals(a.simpleName)
                        || "javax.persistence.OneToMany".equals(a.qualifiedName) || "jakarta.persistence.OneToMany".equals(a.qualifiedName)
                        || "javax.persistence.OneToOne".equals(a.qualifiedName) || "jakarta.persistence.OneToOne".equals(a.qualifiedName)) {
                    String v = a.values == null ? null : a.values.get("orphanRemoval");
                    if (v != null) tags.put("jpaOrphanRemoval", v);
                }
            }
        }
        return Map.copyOf(tags);
    }
}

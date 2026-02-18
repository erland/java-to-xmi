package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RelationHeuristicsTest {

    @Test
    void jpaEmbeddedAndElementCollection_forceAttributeOnly() {
        JAnnotationUse embedded = new JAnnotationUse("Embedded", "jakarta.persistence.Embedded", Map.of());
        JField f1 = new JField("addr", "Address", null, JVisibility.PRIVATE, false, false, List.of(embedded));
        assertFalse(RelationHeuristics.shouldCreateAssociation(f1, dummyOwner(), AssociationPolicy.JPA_ONLY, true));
        assertFalse(RelationHeuristics.shouldCreateAssociation(f1, dummyOwner(), AssociationPolicy.RESOLVED, true));

        JAnnotationUse ec = new JAnnotationUse("ElementCollection", "jakarta.persistence.ElementCollection", Map.of());
        JField f2 = new JField("tags", "List<String>", null, JVisibility.PRIVATE, false, false, List.of(ec));
        assertFalse(RelationHeuristics.shouldCreateAssociation(f2, dummyOwner(), AssociationPolicy.SMART, true));
    }

    @Test
    void jpaOnly_createsOnlyWhenJpaRelationshipPresent() {
        JField plain = new JField("customer", "Customer", null, JVisibility.PRIVATE, false, false, List.of());
        assertFalse(RelationHeuristics.shouldCreateAssociation(plain, dummyOwner(), AssociationPolicy.JPA_ONLY, true));

        JAnnotationUse m2o = new JAnnotationUse("ManyToOne", "jakarta.persistence.ManyToOne", Map.of());
        JField rel = new JField("customer", "Customer", null, JVisibility.PRIVATE, false, false, List.of(m2o));
        assertTrue(RelationHeuristics.shouldCreateAssociation(rel, dummyOwner(), AssociationPolicy.JPA_ONLY, false));
    }

    @Test
    void resolved_policy_usesResolutionFlag() {
        JField f = new JField("repo", "CustomerRepository", null, JVisibility.PRIVATE, false, false, List.of());
        assertFalse(RelationHeuristics.shouldCreateAssociation(f, dummyOwner(), AssociationPolicy.RESOLVED, false));
        assertTrue(RelationHeuristics.shouldCreateAssociation(f, dummyOwner(), AssociationPolicy.RESOLVED, true));
    }

    @Test
    void smart_policy_skipsValueTypes_evenIfResolved() {
        TypeRef tr = TypeRef.simple("String", "String", "java.lang.String");
        JField f = new JField("name", "String", tr, JVisibility.PRIVATE, false, false, List.of());
        assertFalse(RelationHeuristics.shouldCreateAssociation(f, dummyOwner(), AssociationPolicy.SMART, true));
    }

    @Test
    void orphanRemoval_onOneToMany_orOneToOne_yieldsCompositeAggregation() {
        JAnnotationUse o2m = new JAnnotationUse("OneToMany", "jakarta.persistence.OneToMany", Map.of("orphanRemoval", "true"));
        JField f = new JField("orders", "List<Order>", null, JVisibility.PRIVATE, false, false, List.of(o2m));
        assertEquals(AggregationKind.COMPOSITE_LITERAL, RelationHeuristics.aggregationKindFor(f));

        JAnnotationUse o2o = new JAnnotationUse("OneToOne", "jakarta.persistence.OneToOne", Map.of("orphanRemoval", "true"));
        JField f2 = new JField("address", "Address", null, JVisibility.PRIVATE, false, false, List.of(o2o));
        assertEquals(AggregationKind.COMPOSITE_LITERAL, RelationHeuristics.aggregationKindFor(f2));
    }

    private static JType dummyOwner() {
        return new JType("p", "Owner", "p.Owner", null,
                JTypeKind.CLASS, JVisibility.PUBLIC,
                false, false, false,
                null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}

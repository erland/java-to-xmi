package info.isaksson.erland.javatoxmi.uml;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.TypeRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MultiplicityResolverJpaTest {

    @Test
    void manyToOne_defaultIsZeroToOne() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef ref = TypeRef.simple("Customer", "Customer", "com.acme.Customer");

        JAnnotationUse rel = new JAnnotationUse("ManyToOne", "jakarta.persistence.ManyToOne", null);
        MultiplicityResolver.Result res = r.resolve(ref, List.of(rel));
        assertEquals(0, res.lower);
        assertEquals(1, res.upper);
        assertEquals("ManyToOne", res.tags.get("jpaRelation"));
    }

    @Test
    void manyToOne_optionalFalse_makesLowerOne() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef ref = TypeRef.simple("Customer", "Customer", "com.acme.Customer");

        Map<String, String> values = new LinkedHashMap<>();
        values.put("optional", "false");
        JAnnotationUse rel = new JAnnotationUse("ManyToOne", "jakarta.persistence.ManyToOne", values);

        MultiplicityResolver.Result res = r.resolve(ref, List.of(rel));
        assertEquals(1, res.lower);
        assertEquals(1, res.upper);
        assertEquals("ManyToOne", res.tags.get("jpaRelation"));
        assertTrue(res.tags.get("nullableSource").contains("optional=false"));
    }

    @Test
    void joinColumn_nullableFalse_makesLowerOne() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef ref = TypeRef.simple("Customer", "Customer", "com.acme.Customer");

        JAnnotationUse rel = new JAnnotationUse("ManyToOne", "jakarta.persistence.ManyToOne", null);

        Map<String, String> jcVals = new LinkedHashMap<>();
        jcVals.put("nullable", "false");
        JAnnotationUse joinCol = new JAnnotationUse("JoinColumn", "jakarta.persistence.JoinColumn", jcVals);

        MultiplicityResolver.Result res = r.resolve(ref, List.of(rel, joinCol));
        assertEquals(1, res.lower);
        assertEquals(1, res.upper);
        assertEquals("JoinColumn.nullable=false", res.tags.get("nullableSource"));
    }

    @Test
    void oneToMany_isZeroToMany() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef list = TypeRef.param("List<Order>", "List", "java.util.List", List.of(TypeRef.simple("Order", "Order", "com.acme.Order")));

        JAnnotationUse rel = new JAnnotationUse("OneToMany", "jakarta.persistence.OneToMany", null);
        MultiplicityResolver.Result res = r.resolve(list, List.of(rel));
        assertEquals(0, res.lower);
        assertEquals(MultiplicityResolver.STAR, res.upper);
        assertEquals("OneToMany", res.tags.get("jpaRelation"));
    }
}

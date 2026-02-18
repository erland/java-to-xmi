package se.erland.javatoxmi.uml;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.TypeRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MultiplicityResolverValidationTest {

    @Test
    void notNull_tightensLowerBound_toOne() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef ref = TypeRef.simple("Foo", "Foo", "com.acme.Foo");

        MultiplicityResolver.Result res = r.resolve(ref, List.of(new JAnnotationUse("NotNull", "jakarta.validation.constraints.NotNull", null)));
        assertEquals(1, res.lower);
        assertEquals(1, res.upper);
    }

    @Test
    void sizeMinMax_tightensCollectionBounds() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef list = TypeRef.param("List<Foo>", "List", "java.util.List", List.of(TypeRef.simple("Foo", "Foo", "com.acme.Foo")));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("min", "1");
        values.put("max", "3");
        JAnnotationUse size = new JAnnotationUse("Size", "jakarta.validation.constraints.Size", values);

        MultiplicityResolver.Result res = r.resolve(list, List.of(size));
        assertEquals(1, res.lower);
        assertEquals(3, res.upper);
        assertEquals("1", res.tags.get("validationSizeMin"));
        assertEquals("3", res.tags.get("validationSizeMax"));
    }

    @Test
    void notEmpty_tightensLowerBound_onCollection() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef set = TypeRef.param("Set<Foo>", "Set", "java.util.Set", List.of(TypeRef.simple("Foo", "Foo", "")));

        JAnnotationUse notEmpty = new JAnnotationUse("NotEmpty", "jakarta.validation.constraints.NotEmpty", null);
        MultiplicityResolver.Result res = r.resolve(set, List.of(notEmpty));
        assertEquals(1, res.lower);
        assertEquals(MultiplicityResolver.STAR, res.upper);
    }
}

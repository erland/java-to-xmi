package se.erland.javatoxmi.uml;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.WildcardBoundKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiplicityResolverContainerTest {

    @Test
    void array_isUnbounded_andTagsElementType() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef elem = TypeRef.simple("Foo", "Foo", "com.acme.Foo");
        TypeRef arr = TypeRef.array("Foo[]", elem, 1);

        MultiplicityResolver.Result res = r.resolve(arr, List.of());
        assertEquals(0, res.lower);
        assertEquals(MultiplicityResolver.STAR, res.upper);
        assertEquals("true", res.tags.get("isArray"));
        assertEquals("com.acme.Foo", res.tags.get("elementType"));
    }

    @Test
    void optional_isZeroToOne_andTagsElementType() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef opt = TypeRef.param("Optional<Foo>", "Optional", "java.util.Optional",
                List.of(TypeRef.simple("Foo", "Foo", "com.acme.Foo")));

        MultiplicityResolver.Result res = r.resolve(opt, List.of());
        assertEquals(0, res.lower);
        assertEquals(1, res.upper);
        assertEquals("Optional", res.tags.get("containerKind"));
        assertEquals("com.acme.Foo", res.tags.get("elementType"));
    }

    @Test
    void list_isUnbounded_andTagsCollectionAndElementType() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef list = TypeRef.param("List<Foo>", "List", "java.util.List",
                List.of(TypeRef.simple("Foo", "Foo", "com.acme.Foo")));

        MultiplicityResolver.Result res = r.resolve(list, List.of());
        assertEquals(0, res.lower);
        assertEquals(MultiplicityResolver.STAR, res.upper);
        assertEquals("List", res.tags.get("collectionKind"));
        assertEquals("com.acme.Foo", res.tags.get("elementType"));
    }

    @Test
    void wildcardDoesNotBreakResolution() {
        MultiplicityResolver r = new MultiplicityResolver();
        TypeRef wc = TypeRef.wildcard("? extends Foo", WildcardBoundKind.EXTENDS, TypeRef.simple("Foo", "Foo", ""));
        TypeRef list = TypeRef.param("List<? extends Foo>", "List", "java.util.List", List.of(wc));

        MultiplicityResolver.Result res = r.resolve(list, List.of());
        assertEquals(0, res.lower);
        assertEquals(MultiplicityResolver.STAR, res.upper);
        assertEquals("List", res.tags.get("collectionKind"));
        assertNotNull(res.tags.get("elementType"));
    }
}

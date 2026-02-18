package se.erland.javatoxmi.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeRefTest {

    @Test
    void constructor_isNullSafe_andCopiesArgs() {
        TypeRef arg = TypeRef.simple("Foo", "Foo", "com.acme.Foo");
        TypeRef t = new TypeRef(null, null, null, null, List.of(arg), -2, null, null, null);

        assertEquals("", t.raw);
        assertEquals(TypeRefKind.SIMPLE, t.kind);
        assertEquals("", t.simpleName);
        assertEquals("", t.qnameHint);
        assertEquals(1, t.args.size());
        assertEquals("Foo", t.args.get(0).raw);
        assertEquals(0, t.arrayDims);
        assertEquals("", t.typeVarName);
        assertEquals(WildcardBoundKind.UNBOUNDED, t.wildcardBoundKind);
        assertNull(t.wildcardBoundType);
    }

    @Test
    void factories_createExpectedKinds() {
        TypeRef simple = TypeRef.simple("String", "String", "java.lang.String");
        assertEquals(TypeRefKind.SIMPLE, simple.kind);

        TypeRef arr = TypeRef.array("Foo[]", TypeRef.simple("Foo", "Foo", ""), 1);
        assertEquals(TypeRefKind.ARRAY, arr.kind);
        assertEquals(1, arr.arrayDims);
        assertEquals(1, arr.args.size());

        TypeRef param = TypeRef.param("List<Foo>", "List", "java.util.List", List.of(TypeRef.simple("Foo", "Foo", "")));
        assertEquals(TypeRefKind.PARAM, param.kind);
        assertEquals(1, param.args.size());

        TypeRef tv = TypeRef.typeVar("T", "T");
        assertEquals(TypeRefKind.TYPEVAR, tv.kind);
        assertEquals("T", tv.typeVarName);

        TypeRef wc = TypeRef.wildcard("? extends Foo", WildcardBoundKind.EXTENDS, TypeRef.simple("Foo", "Foo", ""));
        assertEquals(TypeRefKind.WILDCARD, wc.kind);
        assertEquals(WildcardBoundKind.EXTENDS, wc.wildcardBoundKind);
        assertNotNull(wc.wildcardBoundType);
    }
}

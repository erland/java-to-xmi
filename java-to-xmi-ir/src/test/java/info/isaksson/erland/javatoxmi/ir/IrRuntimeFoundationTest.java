package info.isaksson.erland.javatoxmi.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IrRuntimeFoundationTest {

    @Test
    void normalizeTaggedValues_ordersFrameworkThenRuntimeThenOther() {
        IrModel in = new IrModel(
                "1.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new IrTaggedValue("z", "3"),
                        new IrTaggedValue(IrRuntime.Tags.PATH, "/a"),
                        new IrTaggedValue("framework", "jakarta"),
                        new IrTaggedValue(IrRuntime.Tags.HTTP_METHOD, "get"),
                        new IrTaggedValue("a", "1"),
                        new IrTaggedValue("runtime.zzz", "2")
                )
        );


        IrModel out = IrNormalizer.normalize(in);
        assertNotNull(out);
        assertEquals(6, out.taggedValues.size());

        // framework first
        assertEquals("framework", out.taggedValues.get(0).key);

        // then runtime.* (sorted by key then value)
        assertTrue(out.taggedValues.get(1).key.startsWith("runtime."));
        assertTrue(out.taggedValues.get(2).key.startsWith("runtime."));
        assertTrue(out.taggedValues.get(3).key.startsWith("runtime."));

                // exact runtime ordering by key
        assertEquals(IrRuntime.Tags.HTTP_METHOD, out.taggedValues.get(1).key);
        assertEquals(IrRuntime.Tags.PATH, out.taggedValues.get(2).key);
        assertEquals("runtime.zzz", out.taggedValues.get(3).key);

// then other keys
        assertEquals("a", out.taggedValues.get(4).key);
        assertEquals("z", out.taggedValues.get(5).key);

        // and values were preserved (by key lookup)
        String pathVal = out.taggedValues.stream()
                .filter(tv -> IrRuntime.Tags.PATH.equals(tv.key))
                .map(tv -> tv.value)
                .findFirst().orElse(null);
        assertEquals("/a", pathVal);
    }

    @Test
    void canonicalizers_areDeterministic() {
        assertEquals("/api/v1", IrRuntime.normalizePath("  api\\v1// "));
        assertEquals("/", IrRuntime.normalizePath("/"));
        assertEquals("GET", IrRuntime.normalizeHttpMethod(" get "));
        assertEquals("my topic", IrRuntime.normalizeDestination("  my   topic  "));
        assertEquals("some.key", IrRuntime.normalizeConfigKey(" some. key "));
        assertEquals("2_1", IrRuntime.normalizeMigrationVersion("V2_1"));
        assertEquals("2", IrRuntime.normalizeMigrationVersion("2"));
    }
}

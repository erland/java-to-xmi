package info.isaksson.erland.javatoxmi.emitter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EmitterWarningsDeterminismTest {

    @Test
    public void warningsAreSortedDeterministically() {
        EmitterWarnings w = new EmitterWarnings();
        w.warn("B", "bbb", Map.of("x", "2"));
        w.warn("A", "ccc", Map.of("a", "1"));
        w.warn("A", "bbb", Map.of("z", "9"));
        w.warn("A", "bbb", Map.of("a", "0"));

        List<EmitterWarning> out = w.toDeterministicList();
        assertEquals(4, out.size());
        assertEquals("A", out.get(0).code);
        assertEquals("bbb", out.get(0).message);
        assertEquals("A", out.get(1).code);
        assertEquals("bbb", out.get(1).message);
        assertEquals("A", out.get(2).code);
        assertEquals("ccc", out.get(2).message);
        assertEquals("B", out.get(3).code);
    }
}

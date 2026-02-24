package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.ir.IrJson;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures XMI output is byte-for-byte stable across repeated emissions for the same IR input and options.
 *
 * <p>This is a "golden-style" determinism check without pinning a specific expected XMI snapshot.</p>
 */
public class XmiByteForByteDeterminismTest {

    @Test
    void xmiIsByteForByteStableAcrossRuns_forGoldenIrFixtures() throws Exception {
        List<String> fixtures = List.of(
                "src/test/resources/ir/golden/java-mini.json",
                "src/test/resources/ir/golden/angular-mini.json",
                "src/test/resources/ir/golden/react-mini.json"
        );

        for (String fixturePath : fixtures) {
            Path fixture = Path.of(fixturePath);
            assertTrue(Files.exists(fixture), "golden IR fixture must exist: " + fixture);

            IrModel ir = IrJson.read(fixture);

            assertStable(ir, EmitterOptions.defaults(fixture.getFileName().toString()).withStereotypes(false), fixturePath + " (no stereotypes)");
            assertStable(ir, EmitterOptions.defaults(fixture.getFileName().toString()).withStereotypes(true), fixturePath + " (with stereotypes)");
        }
    }

    private static void assertStable(IrModel ir, EmitterOptions options, String label) throws Exception {
        XmiEmitter emitter = new XmiEmitter();

        XmiEmitter.StringResult r1 = emitter.emitToStringWithResult(ir, options);
        XmiEmitter.StringResult r2 = emitter.emitToStringWithResult(ir, options);

        byte[] b1 = r1.xmi.getBytes(StandardCharsets.UTF_8);
        byte[] b2 = r2.xmi.getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(b1, b2, "XMI must be byte-for-byte stable for " + label);

        // Warnings must also be stable (sorted deterministically).
        assertEquals(r1.build.warnings.size(), r2.build.warnings.size(), "warning count must be stable for " + label);
        for (int i = 0; i < r1.build.warnings.size(); i++) {
            EmitterWarning w1 = r1.build.warnings.get(i);
            EmitterWarning w2 = r2.build.warnings.get(i);
            assertEquals(w1.code, w2.code, "warning code stable for " + label);
            assertEquals(w1.message, w2.message, "warning message stable for " + label);
            assertEquals(w1.context, w2.context, "warning context stable for " + label);
        }
    }
}

package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.ir.IrJson;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class XmiEmitterSmokeTest {

    @Test
    void emitsXmiFromIrReactExample() throws Exception {
        Path golden = Path.of("src/test/resources/ir/golden/react-mini.json");
        assertTrue(Files.exists(golden), "golden IR fixture must exist: " + golden);

        IrModel ir = IrJson.read(golden);

        Path out = Files.createTempDirectory("xmi-emitter-test").resolve("model.xmi");
        new XmiEmitter().emit(ir, EmitterOptions.defaults("react-mini"), out);

        assertTrue(Files.exists(out), "XMI output must exist");
        String xmi = Files.readString(out);
        // Basic sanity: must contain UML model element
        assertTrue(xmi.contains("uml:Model") || xmi.contains("xmi:XMI"), "XMI must contain UML content");
    }
}

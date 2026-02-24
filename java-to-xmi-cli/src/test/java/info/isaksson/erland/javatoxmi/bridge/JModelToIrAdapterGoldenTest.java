package info.isaksson.erland.javatoxmi.bridge;

import info.isaksson.erland.javatoxmi.ir.IrJson;
import info.isaksson.erland.javatoxmi.model.*;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JModelToIrAdapterGoldenTest {

    @Test
    void writesV2StereotypeRegistryAndRefsForLegacyAnnotationStereotype() throws Exception {
        // Build a minimal Java extractor model containing an @interface type.
        JModel jm = new JModel(Path.of("src"), List.of());
        jm.types.add(new JType(
                "p",
                "MyAnno",
                "p.MyAnno",
                null,
                JTypeKind.ANNOTATION,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        var adapter = new JModelToIrAdapter();
        var ir = adapter.toIr(jm);

        String json = IrJson.toJsonString(ir);
        String fixture = readResource("/ir/golden/jmodel-to-ir-annotation.json");

        assertEquals(fixture, json);
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = JModelToIrAdapterGoldenTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

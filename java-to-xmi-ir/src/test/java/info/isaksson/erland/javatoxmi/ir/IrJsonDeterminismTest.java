package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class IrJsonDeterminismTest {

    @Test
    void writeMatchesGoldenReact() throws Exception {
        assertGoldenRoundTrip("ir/golden/react-mini.json");
    }

    @Test
    void writeMatchesGoldenAngular() throws Exception {
        assertGoldenRoundTrip("ir/golden/angular-mini.json");
    }

    @Test
    void writeMatchesGoldenJava() throws Exception {
        assertGoldenRoundTrip("ir/golden/java-mini.json");
    }

        private static void assertGoldenRoundTrip(String resourcePath) throws IOException, URISyntaxException {
        Path goldenPath = Path.of(IrJsonDeterminismTest.class.getClassLoader().getResource(resourcePath).toURI());
        String golden = Files.readString(goldenPath, StandardCharsets.UTF_8);

        IrModel model = IrJson.read(goldenPath);

        // Parse once so the test is resilient to whitespace/pretty-print differences.
        ObjectMapper om = new ObjectMapper();
        JsonNode goldenNode = om.readTree(golden);

        // 1) toJsonString must be semantically equal to golden
        String rendered = IrJson.toJsonString(model);
        JsonNode renderedNode = om.readTree(rendered);
        assertEquals(goldenNode, renderedNode, "Rendered JSON must be semantically equal to golden fixture.");

        // 2) writing to a file is deterministic and semantically equal to golden
        Path tmp = Files.createTempFile("irjson-", ".json");
        IrJson.write(model, tmp);
        String written = Files.readString(tmp, StandardCharsets.UTF_8);
        JsonNode writtenNode = om.readTree(written);
        assertEquals(goldenNode, writtenNode, "Written JSON must be semantically equal to golden fixture.");

        // 3) writing twice yields identical textual output (determinism)
        Path tmp2 = Files.createTempFile("irjson-", ".json");
        IrJson.write(model, tmp2);
        String written2 = Files.readString(tmp2, StandardCharsets.UTF_8);
        assertEquals(written, written2, "Writing twice must produce identical output.");
    }

}

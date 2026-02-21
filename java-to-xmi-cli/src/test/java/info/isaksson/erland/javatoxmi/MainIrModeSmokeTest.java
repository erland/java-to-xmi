package info.isaksson.erland.javatoxmi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainIrModeSmokeTest {

    @Test
    void runsInIrModeAndEmitsXmi() throws IOException {
        // Copy bundled golden IR fixture to a temp file (so Main can read via filesystem path).
        Path tmpDir = Files.createTempDirectory("j2x-ir-mode-");
        Path ir = tmpDir.resolve("react-mini.json");
        try (var in = MainIrModeSmokeTest.class.getResourceAsStream("/ir/golden/react-mini.json")) {
            assertTrue(in != null, "fixture must exist in test resources");
            Files.copy(in, ir);
        }

        Path outDir = tmpDir.resolve("out");
        Path xmi = outDir.resolve("model.xmi");

        int code = Main.run(new String[] {
                "--ir", ir.toString(),
                "--output", outDir.toString(),
                "--no-stereotypes" // keep smoke test simple/fast
        });
        assertEquals(0, code);
        assertTrue(Files.exists(xmi), "XMI must be written: " + xmi);
        String s = Files.readString(xmi);
        assertTrue(s.contains("xmi:"), "Output should look like XMI");
    }
}

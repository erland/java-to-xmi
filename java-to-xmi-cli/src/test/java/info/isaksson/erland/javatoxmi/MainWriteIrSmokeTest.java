package info.isaksson.erland.javatoxmi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MainWriteIrSmokeTest {

    @Test
    void writesIrWhenRequested() throws IOException {
        Path tmpDir = Files.createTempDirectory("j2x-write-ir-");
        Path outDir = tmpDir.resolve("out");
        Path irOut = outDir.resolve("model.ir.json");

        int code = Main.run(new String[] {
                "--source", TestRepoPaths.resolveSamplesMini().toString(),
                "--output", outDir.toString(),
                "--write-ir", irOut.toString(),
                "--no-stereotypes"
        });
        assertEquals(0, code);
        assertTrue(Files.exists(irOut), "IR file must be written: " + irOut);
        String s = Files.readString(irOut);
        assertTrue(s.contains("\"schemaVersion\""), "IR JSON must have schemaVersion");
    }
}

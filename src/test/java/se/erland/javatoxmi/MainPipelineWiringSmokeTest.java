package se.erland.javatoxmi;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.uml.JavaAnnotationProfileBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

class MainPipelineWiringSmokeTest {

    @Test
    void noStereotypesFlagControlsProfileAndInjection() throws Exception {
        Path projectDir = Files.createTempDirectory("j2x-main-wiring-");
        Path srcDir = projectDir.resolve("src");
        Files.createDirectories(srcDir);

        // Keep it minimal; just an annotation and a class using it.
        Files.writeString(srcDir.resolve("Ann.java"),
                "package demo;\n" +
                "public @interface Ann { String value(); }\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.writeString(srcDir.resolve("A.java"),
                "package demo;\n" +
                "@Ann(\"x\")\n" +
                "public class A { }\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // 1) Default run -> profile URI should appear in XMI (stereotypes enabled by default)
        Path out1 = projectDir.resolve("out1");
        Files.createDirectories(out1);
        Path xmi1 = out1.resolve("model.xmi");

        int code1 = Main.run(new String[] {
                "--source", projectDir.toString(),
                "--output", xmi1.toString()
        });
        assertEquals(0, code1, "Expected success exit code");
        assertTrue(Files.exists(xmi1), "Expected XMI output to exist: " + xmi1);

        String xmiText1 = Files.readString(xmi1);
        assertTrue(xmiText1.contains(JavaAnnotationProfileBuilder.PROFILE_URI),
                "Expected profile URI to be present when stereotypes are enabled");

        // 2) --no-stereotypes true -> profile URI should NOT appear
        Path out2 = projectDir.resolve("out2");
        Files.createDirectories(out2);
        Path xmi2 = out2.resolve("model.xmi");

        int code2 = Main.run(new String[] {
                "--source", projectDir.toString(),
                "--output", xmi2.toString(),
                "--no-stereotypes"
        });
        assertEquals(0, code2, "Expected success exit code with --no-stereotypes");
        assertTrue(Files.exists(xmi2), "Expected XMI output to exist: " + xmi2);

        String xmiText2 = Files.readString(xmi2);
        assertFalse(xmiText2.contains(JavaAnnotationProfileBuilder.PROFILE_URI),
                "Expected profile URI to be absent when --no-stereotypes is true");
    }
}

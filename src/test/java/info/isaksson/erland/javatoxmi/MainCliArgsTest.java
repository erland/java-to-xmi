package info.isaksson.erland.javatoxmi;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.uml.AssociationPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MainCliArgsTest {

    @Test
    void parsesBasicArgsAndExcludeForms() {
        String[] args = new String[] {
                "--source", "samples/mini/src/main/java",
                "--output", "target/out",
                "--exclude", "**/generated/**",
                "--exclude=**/*Test.java",
                "--include-tests",
                "--name", "MyModel",
                "--report", "target/out/report.md",
                "--fail-on-unresolved", "true",
                "--associations", "smart"
        };

        Main.CliArgs parsed = Main.CliArgs.parse(args);
        assertEquals("samples/mini/src/main/java", parsed.source);
        assertEquals("target/out", parsed.output);
        assertTrue(parsed.includeTests);
        assertEquals("MyModel", parsed.name);
        assertEquals("target/out/report.md", parsed.report);
        assertTrue(parsed.failOnUnresolved);
        assertEquals(AssociationPolicy.SMART, parsed.associationPolicy);
        assertEquals(List.of("**/generated/**", "**/*Test.java"), parsed.excludes);
    }

    @Test
    void acceptsBarePathAsSourceShorthand() {
        Main.CliArgs parsed = Main.CliArgs.parse(new String[] {"samples/mini/src/main/java"});
        assertEquals("samples/mini/src/main/java", parsed.source);
    }

    @Test
    void parseBooleanRejectsInvalidValues() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Main.CliArgs.parse(new String[] {"--source", "x", "--fail-on-unresolved", "maybe"}));
        assertTrue(ex.getMessage().contains("Invalid boolean"));
    }

    @Test
    void unknownFlagThrows() {
        assertThrows(IllegalArgumentException.class, () -> Main.CliArgs.parse(new String[] {"--nope"}));
    }

    @Test
    void missingValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> Main.CliArgs.parse(new String[] {"--source"}));
    }
}

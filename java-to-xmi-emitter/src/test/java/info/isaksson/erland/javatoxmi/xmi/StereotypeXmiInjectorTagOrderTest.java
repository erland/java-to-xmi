package info.isaksson.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StereotypeXmiInjectorTagOrderTest {

    @Test
    void injectedTagsAreSortedByNameForStableOutput() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-tagorder");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("Anno.java"),
                "package com.acme;\n" +
                        "public @interface Anno { String b(); String a(); }\n",
                StandardCharsets.UTF_8);

        // Deliberately specify tag values in reverse order in the source.
        Files.writeString(src.resolve("C.java"),
                "package com.acme;\n" +
                        "@Anno(b=\"2\", a=\"1\")\n" +
                        "public class C { }\n",
                StandardCharsets.UTF_8);

        List<Path> files = List.of(src.resolve("Anno.java"), src.resolve("C.java"));
        JModel jm = new JavaExtractor().extract(root, files);
        var uml = new UmlBuilder().build(jm, "java-to-xmi").umlModel;

        Path out = Files.createTempFile("java-to-xmi-tagorder", ".xmi");
        XmiWriter.write(uml, jm, out);
        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        // Tags are emitted as attributes; ensure deterministic ordering a before b in the emitted element.
        int el = xmi.indexOf(":Anno");
        assertTrue(el >= 0, "Expected stereotype application element for Anno");
        int idxA = xmi.indexOf(" a=\"1\"", el);
        int idxB = xmi.indexOf(" b=\"2\"", el);
        assertTrue(idxA >= 0, "Expected injected attribute 'a' to be present");
        assertTrue(idxB >= 0, "Expected injected attribute 'b' to be present");
        assertTrue(idxA < idxB, "Expected attributes to be emitted in sorted order: a before b");
    }
}

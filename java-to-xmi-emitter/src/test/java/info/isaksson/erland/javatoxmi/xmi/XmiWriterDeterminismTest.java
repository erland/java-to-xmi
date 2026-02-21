package info.isaksson.erland.javatoxmi.xmi;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XmiWriterDeterminismTest {

    @Test
    void producesStableOutputAcrossRunsAndInputFileOrder() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-determinism");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("A.java"),
                "package com.acme;\n" +
                        "public class A { B b; }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("B.java"),
                "package com.acme;\n" +
                        "public class B { }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("Entity.java"),
                "package com.acme;\n" +
                        "public @interface Entity { String value() default \"\"; }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("C.java"),
                "package com.acme;\n" +
                        "@Entity(\"c\")\n" +
                        "public class C { }\n",
                StandardCharsets.UTF_8);

        Path a = src.resolve("A.java");
        Path b = src.resolve("B.java");
        Path entity = src.resolve("Entity.java");
        Path c = src.resolve("C.java");

        // Run 1 (forward order)
        List<Path> files1 = List.of(a, b, entity, c);
        JModel jm1 = new JavaExtractor().extract(root, files1);
        var uml1 = new UmlBuilder().build(jm1, "java-to-xmi").umlModel;
        Path out1 = Files.createTempFile("java-to-xmi-d1", ".xmi");
        XmiWriter.write(uml1, jm1, out1);
        String x1 = Files.readString(out1, StandardCharsets.UTF_8);

        // Run 2 (different file order)
        List<Path> files2 = List.of(c, entity, b, a);
        JModel jm2 = new JavaExtractor().extract(root, files2);
        var uml2 = new UmlBuilder().build(jm2, "java-to-xmi").umlModel;
        Path out2 = Files.createTempFile("java-to-xmi-d2", ".xmi");
        XmiWriter.write(uml2, jm2, out2);
        String x2 = Files.readString(out2, StandardCharsets.UTF_8);

        assertEquals(x1, x2);
    }
}

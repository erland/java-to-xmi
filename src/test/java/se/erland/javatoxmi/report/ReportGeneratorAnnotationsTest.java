package se.erland.javatoxmi.report;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportGeneratorAnnotationsTest {

    @Test
    void reportContainsAnnotationSummaryAndSortedKeys() throws Exception {
        Path root = Files.createTempDirectory("java-to-xmi-report-ann");
        Path src = root.resolve("src/main/java/com/acme");
        Files.createDirectories(src);

        Files.writeString(src.resolve("Entity.java"),
                "package com.acme;\n" +
                        "public @interface Entity { String value() default \"\"; String z() default \"\"; }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("A.java"),
                "package com.acme;\n" +
                        "@Entity(value=\"a\", z=\"1\")\n" +
                        "public class A { }\n",
                StandardCharsets.UTF_8);

        Files.writeString(src.resolve("B.java"),
                "package com.acme;\n" +
                        "@Entity(z=\"2\", value=\"b\")\n" +
                        "public class B { }\n",
                StandardCharsets.UTF_8);

        var files = List.of(src.resolve("Entity.java"), src.resolve("A.java"), src.resolve("B.java"));
        JModel jModel = new JavaExtractor().extract(root, files);
        var uml = new UmlBuilder().build(jModel, "TestModel");

        Path outDir = Files.createTempDirectory("java-to-xmi-report-out");
        Path xmiOut = outDir.resolve("model.xmi");
        Path reportOut = outDir.resolve("report.md");
        Files.writeString(xmiOut, "<dummy/>");

        ReportGenerator.writeMarkdown(
                reportOut,
                root,
                xmiOut,
                jModel,
                uml.umlModel,
                uml.stats,
                files,
                false,
                List.of(),
                false
        );

        String md = Files.readString(reportOut);

        assertTrue(md.contains("## Annotations (stereotypes)"));
        assertTrue(md.contains("| Annotation | Uses | Keys |"));

        // Expect the annotation to be present and keys to be sorted (value, z)
        // Keys column is rendered as: "value, z" (sorted lexicographically)
        assertTrue(md.contains("`com.acme.Entity`"));
        assertTrue(md.contains("value, z"), "Expected sorted keys 'value, z' in the report");

        // Summary counts should be present
        assertTrue(md.contains("- Annotation uses: **2**"));
        assertTrue(md.contains("- Annotated types: **2**"));
    }
}

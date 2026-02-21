package info.isaksson.erland.javatoxmi.report;

import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportGeneratorTest {

    @Test
    void writesMarkdownWithExpectedSections() throws Exception {
        // Keep source + discovered files consistently RELATIVE (ReportGenerator uses sourcePath.relativize(p))
        Path source = Path.of("samples/mini/src/main/java").normalize();
        var files = SourceScanner.scan(source, List.of(), false);

        JModel jModel = new JavaExtractor().extract(source, files);

        UmlBuilder.Result uml = new UmlBuilder().build(jModel, "MiniModel");
        Model umlModel = uml.umlModel;

        Path tmp = Files.createTempDirectory("java-to-xmi-report-test");
        Path xmiOut = tmp.resolve("model.xmi");
        Path reportOut = tmp.resolve("report.md");

        Files.writeString(xmiOut, "<dummy/>");

        ReportGenerator.writeMarkdown(
                reportOut,
                source,
                xmiOut,
                jModel,
                umlModel,
                uml.stats,
                files,
                false,
                List.of(),
                false
        );

        String md = Files.readString(reportOut);
        assertTrue(md.contains("# java-to-xmi report"));
        assertTrue(md.contains("## Summary"));
        assertTrue(md.contains("## Discovered files"));
        assertTrue(md.contains("## Extracted types"));
        assertTrue(md.contains("## External type references (stubbed)"));
        assertTrue(md.contains("## Unresolved type references (unknown)"));
    }
}

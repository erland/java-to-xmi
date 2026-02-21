package info.isaksson.erland.javatoxmi.xmi;

import info.isaksson.erland.javatoxmi.testutil.TestPaths;
import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterTest {

    @Test
    void xmiExportIsDeterministicAcrossTwoRuns() throws Exception {
        Path samples = TestPaths.resolveInRepo("samples/mini");
        assertTrue(Files.exists(samples), "Expected samples/mini to exist");

        List<Path> javaFiles = SourceScanner.scan(samples, List.of(), false);

        // Important: Build two *independent* JModels to avoid any accidental mutation or object
        // ownership interactions affecting subsequent builds.
        JavaExtractor extractor1 = new JavaExtractor();
        JModel jModel1 = extractor1.extract(samples, javaFiles);

        JavaExtractor extractor2 = new JavaExtractor();
        JModel jModel2 = extractor2.extract(samples, javaFiles);

        Model uml1 = new UmlBuilder().build(jModel1, "java-to-xmi").umlModel;
        Model uml2 = new UmlBuilder().build(jModel2, "java-to-xmi").umlModel;

        Path tmpDir = Files.createTempDirectory("java-to-xmi-test");
        Path out1 = tmpDir.resolve("model1.xmi");
        Path out2 = tmpDir.resolve("model2.xmi");

        XmiWriter.write(uml1, out1);
        XmiWriter.write(uml2, out2);

        long size1 = Files.size(out1);
        long size2 = Files.size(out2);

        // Fail early with a clear message if the two outputs differ in size.
        // This avoids a confusing AssertArrayEquals length mismatch when one file is near-empty.
        assertEquals(size1, size2,
                "XMI outputs differ in size (model1=" + size1 + " bytes, model2=" + size2 + " bytes). " +
                        "If one is tiny, the second run likely produced an empty model. " +
                        "Re-run with -DjavaToXmi.debugTests=true to print excerpts.");

        // If one of the outputs is suspiciously tiny, fail fast with diagnostics.
        // This helps distinguish "nondeterministic serialization" from "second run produced an empty model".
        assertTrue(size1 > 1000, "model1.xmi unexpectedly small: " + size1 + " bytes");
        assertTrue(size2 > 1000, "model2.xmi unexpectedly small: " + size2 + " bytes");

        byte[] b1 = Files.readAllBytes(out1);
        byte[] b2 = Files.readAllBytes(out2);

        // Optional debug prints
        if (Boolean.getBoolean("javaToXmi.debugTests")) {
            System.out.println("[DEBUG] model1.xmi bytes=" + size1);
            System.out.println("[DEBUG] model2.xmi bytes=" + size2);
            System.out.println("[DEBUG] model1 first 200 chars:\n" +
                    new String(b1, 0, Math.min(200, b1.length), StandardCharsets.UTF_8));
            System.out.println("[DEBUG] model2 first 200 chars:\n" +
                    new String(b2, 0, Math.min(200, b2.length), StandardCharsets.UTF_8));
        }

        assertArrayEquals(b1, b2, "XMI output should be byte-for-byte identical across runs");

        String s = new String(b1, StandardCharsets.UTF_8);
        assertTrue(s.contains("uml:Model") || s.contains("Model"), "Expected XMI to contain a UML Model element");
    }

    private static int firstDiffIndex(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) return i;
        }
        return a.length == b.length ? -1 : n;
    }

}

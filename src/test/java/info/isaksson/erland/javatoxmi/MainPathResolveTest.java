package info.isaksson.erland.javatoxmi;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MainPathResolveTest {

    @Test
    void resolveXmiOutputTreatsDotXmiAsFile() throws Exception {
        Method m = Main.class.getDeclaredMethod("resolveXmiOutput", String.class, Path.class);
        m.setAccessible(true);
        Path out = (Path) m.invoke(null, "target/out/custom.xmi", Path.of("samples/mini"));
        assertTrue(out.toString().endsWith("custom.xmi"));
    }

    @Test
    void resolveXmiOutputTreatsDirAsDirectory() throws Exception {
        Method m = Main.class.getDeclaredMethod("resolveXmiOutput", String.class, Path.class);
        m.setAccessible(true);
        Path out = (Path) m.invoke(null, "target/outdir", Path.of("samples/mini"));
        assertTrue(out.toString().endsWith("outdir/model.xmi".replace("/", java.io.File.separator)));
    }

    @Test
    void resolveReportDefaultsToSiblingReportMd() throws Exception {
        Method m = Main.class.getDeclaredMethod("resolveReportOutput", String.class, Path.class);
        m.setAccessible(true);
        Path xmi = Path.of("target/out/model.xmi");
        Path rep = (Path) m.invoke(null, null, xmi);
        assertEquals(xmi.getParent().resolve("report.md").normalize(), rep.normalize());
    }

    @Test
    void resolveReportUsesProvidedPath() throws Exception {
        Method m = Main.class.getDeclaredMethod("resolveReportOutput", String.class, Path.class);
        m.setAccessible(true);
        Path xmi = Path.of("target/out/model.xmi");
        Path rep = (Path) m.invoke(null, "target/custom/report.md", xmi);
        assertTrue(rep.toString().endsWith("target/custom/report.md".replace("/", java.io.File.separator)));
    }
}

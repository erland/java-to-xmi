package se.erland.javatoxmi.uml;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderTest {

    @Test
    void buildsUmlModelObjectGraph() throws Exception {
        Path root = Paths.get("samples/mini").toAbsolutePath().normalize();
        var javaFiles = SourceScanner.scan(root, Collections.emptyList(), true);
        JModel m = new JavaExtractor().extract(root, javaFiles);

        UmlBuilder.Result r = new UmlBuilder().build(m, "mini");
        assertNotNull(r.umlModel);
        assertEquals("mini", r.umlModel.getName());

        // Expect at least the sample types to exist as owned elements somewhere in the model.
        assertTrue(r.stats.classifiersCreated >= 4, "Expected at least 4 classifiers");
        assertTrue(r.stats.packagesCreated >= 1, "Expected at least 1 package");
    }
}

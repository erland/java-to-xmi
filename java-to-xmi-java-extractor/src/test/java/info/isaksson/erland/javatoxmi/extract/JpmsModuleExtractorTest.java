package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JJavaModule;
import info.isaksson.erland.javatoxmi.model.JModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JpmsModuleExtractorTest {

    @Test
    void extractsModulesRequiresExportsOpens() throws Exception {
        Path root = Files.createTempDirectory("j2x-jpms");
        // module b
        Path m2 = root.resolve("m2");
        Files.createDirectories(m2);
        Files.writeString(m2.resolve("module-info.java"),
                "module b.mod { exports b.api; }\n");
        Files.createDirectories(m2.resolve("b/api"));
        Files.writeString(m2.resolve("b/api/B.java"),
                "package b.api; public class B {}\n");

        // module a
        Path m1 = root.resolve("m1");
        Files.createDirectories(m1);
        Files.writeString(m1.resolve("module-info.java"),
                "module a.mod { requires transitive b.mod; exports a.api; opens a.internal; }\n");
        Files.createDirectories(m1.resolve("a/api"));
        Files.writeString(m1.resolve("a/api/A.java"),
                "package a.api; public class A {}\n");

        List<Path> files = SourceScanner.scan(root, List.of(), false);
        JModel model = new JavaExtractor().extract(root, files);

        assertNotNull(model.javaModules);
        assertEquals(2, model.javaModules.size());

        JJavaModule a = model.javaModules.stream().filter(m -> m.name.equals("a.mod")).findFirst().orElseThrow();
        JJavaModule b = model.javaModules.stream().filter(m -> m.name.equals("b.mod")).findFirst().orElseThrow();

        assertEquals(List.of("a.api"), a.exports);
        assertEquals(List.of("a.internal"), a.opens);
        assertEquals(1, a.requires.size());
        assertEquals("b.mod", a.requires.get(0).moduleName);
        assertTrue(a.requires.get(0).isTransitive);

        assertEquals(List.of("b.api"), b.exports);
        assertTrue(b.opens.isEmpty());
        assertTrue(b.requires.isEmpty());
    }
}

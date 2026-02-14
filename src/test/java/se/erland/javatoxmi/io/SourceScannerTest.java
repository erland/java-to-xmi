package se.erland.javatoxmi.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SourceScannerTest {

    @Test
    void scansJavaFilesDeterministically_andExcludesCommonBuildDirs(@TempDir Path tmp) throws Exception {
        // Arrange
        Path srcMain = tmp.resolve("src/main/java/com/acme");
        Path target = tmp.resolve("target/generated-sources");
        Files.createDirectories(srcMain);
        Files.createDirectories(target);

        Path a = srcMain.resolve("A.java");
        Path b = srcMain.resolve("B.java");
        Path generated = target.resolve("Gen.java");

        Files.writeString(a, "package com.acme; class A {}");
        Files.writeString(b, "package com.acme; class B {}");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "class Gen {}");

        // Act
        List<Path> files = SourceScanner.scan(tmp, List.of(), false);

        // Assert: stable ordering by relative path + excludes target/
        assertEquals(2, files.size());
        assertEquals("src/main/java/com/acme/A.java", tmp.relativize(files.get(0)).toString().replace("\\", "/"));
        assertEquals("src/main/java/com/acme/B.java", tmp.relativize(files.get(1)).toString().replace("\\", "/"));
    }

    @Test
    void excludesTestFoldersByDefault_butCanInclude(@TempDir Path tmp) throws Exception {
        Path main = tmp.resolve("src/main/java/p/A.java");
        Path test = tmp.resolve("src/test/java/p/ATest.java");
        Files.createDirectories(main.getParent());
        Files.createDirectories(test.getParent());
        Files.writeString(main, "package p; class A {}");
        Files.writeString(test, "package p; class ATest {}");

        List<Path> noTests = SourceScanner.scan(tmp, List.of(), false);
        assertEquals(1, noTests.size());
        assertEquals("src/main/java/p/A.java", tmp.relativize(noTests.get(0)).toString().replace("\\", "/"));

        List<Path> withTests = SourceScanner.scan(tmp, List.of(), true);
        assertEquals(2, withTests.size());
    }

    @Test
    void supportsGlobExcludes(@TempDir Path tmp) throws Exception {
        Path keep = tmp.resolve("src/main/java/p/Keep.java");
        Path gen = tmp.resolve("src/main/java/p/generated/Gen.java");
        Files.createDirectories(keep.getParent());
        Files.createDirectories(gen.getParent());
        Files.writeString(keep, "package p; class Keep {}");
        Files.writeString(gen, "package p; class Gen {}");

        List<Path> files = SourceScanner.scan(tmp, List.of("**/generated/**"), false);

        assertEquals(1, files.size());
        assertEquals("src/main/java/p/Keep.java", tmp.relativize(files.get(0)).toString().replace("\\", "/"));
    }
}

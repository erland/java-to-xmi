package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorTypeDocTest {

    @Test
    void extractsTypeLevelJavadoc_preservesNewlines_collapsesSpaces() throws Exception {
        Path root = Files.createTempDirectory("j2x-doc");
        Path src = root.resolve("p");
        Files.createDirectories(src);
        Path f = src.resolve("A.java");

        String code = "package p;\n" +
                "/**\n" +
                " * Hello   world!\n" +
                " *\n" +
                " * Line  two   with   spaces.\n" +
                " * <p>Keep <b>HTML</b> as-is</p>\n" +
                " */\n" +
                "public class A {}\n";

        Files.writeString(f, code, StandardCharsets.UTF_8);

        JavaExtractor ex = new JavaExtractor();
        JModel m = ex.extract(root, List.of(f));
        assertEquals(1, m.types.size());
        JType t = m.types.get(0);

        String expected = "Hello world!\n" +
                "\n" +
                "Line two with spaces.\n" +
                "<p>Keep <b>HTML</b> as-is</p>";

        assertEquals(expected, t.doc);
    }

    @Test
    void truncatesTypeLevelJavadoc_andAppendsSuffix() {
        String big = "* " + "a".repeat(JavaExtractor.MAX_TYPE_DOC_CHARS + 200);
        String normalized = JavaExtractor.normalizeDocContent(big);
        assertTrue(normalized.length() <= JavaExtractor.MAX_TYPE_DOC_CHARS);
        assertTrue(normalized.endsWith("…(truncated)"));
    }
}

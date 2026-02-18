package se.erland.javatoxmi.xmi;

import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

public class XmiWriterTypeDocCommentTest {

    @Test
    void typeLevelJavaDocSurvivesXmiSerialization() throws Exception {
        Path tmp = Files.createTempDirectory("java-to-xmi-doc");
        Path srcDir = tmp.resolve("src");
        Path pkgDir = srcDir.resolve("p");
        Files.createDirectories(pkgDir);

        String java = "package p;\n" +
                "/**\n" +
                " * Hello   world!\n" +
                " *\n" +
                " * Line two   with   spaces.\n" +
                " * <p>Keep <b>HTML</b> as-is</p>\n" +
                " */\n" +
                "public class C { }\n";
        Files.writeString(pkgDir.resolve("C.java"), java, StandardCharsets.UTF_8);

        List<Path> javaFiles = SourceScanner.scan(srcDir, List.of(), false);
        JavaExtractor extractor = new JavaExtractor();
        JModel jModel = extractor.extract(srcDir, javaFiles);

        Model uml = new UmlBuilder().build(jModel, "java-to-xmi").umlModel;

        Path out = tmp.resolve("model.xmi");
        XmiWriter.write(uml, out);

        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        // Comment should be serialized as ownedComment with a body attribute.
        assertTrue(xmi.contains("ownedComment"), "Expected XMI to contain an ownedComment element");
        assertTrue(xmi.contains("Hello world!"), "Expected JavaDoc content to appear in XMI comment body");
        assertTrue(xmi.contains("Line two with spaces."), "Expected whitespace-normalized line to appear");

        // Parse XML and inspect the Comment body attribute after XML unescaping.
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // secure defaults
        try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}

        var doc = dbf.newDocumentBuilder().parse(out.toFile());
        var nodes = doc.getElementsByTagName("ownedComment");
        assertTrue(nodes.getLength() > 0, "Expected at least one ownedComment node");
        String body = ((org.w3c.dom.Element) nodes.item(0)).getAttribute("body");

        assertTrue(body.contains("<p>Keep <b>HTML</b> as-is</p>"),
                "Expected HTML-ish markup to be preserved in the comment body once XML is parsed");
    }
}

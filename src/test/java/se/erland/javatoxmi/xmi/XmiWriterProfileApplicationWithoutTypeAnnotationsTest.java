package se.erland.javatoxmi.xmi;

import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.JTypeKind;
import se.erland.javatoxmi.model.JVisibility;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.uml.AssociationPolicy;
import se.erland.javatoxmi.uml.UmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test:
 * When only tool tags (from TypeRef-based multiplicity/container inference) exist and there are no
 * type-level Java annotations, we still expect the JavaAnnotations profile to be present and applied
 * so the XMI injector can emit stereotype applications for tool tags.
 */
public class XmiWriterProfileApplicationWithoutTypeAnnotationsTest {

    @Test
    void emitsProfileApplicationAndToolTagsEvenWithoutTypeAnnotations() throws Exception {
        // Build a tiny in-memory IR model:
        // p.Holder has a field List<Foo> foos; no annotations anywhere.
        JModel jm = new JModel(Path.of("."), List.of());

        JType foo = new JType(
                "p",
                "Foo",
                "p.Foo",
                null,
                JTypeKind.CLASS,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        TypeRef fooRef = TypeRef.simple("Foo", "Foo", "p.Foo");
        TypeRef listOfFoo = TypeRef.param("List<Foo>", "List", "java.util.List", List.of(fooRef));

        JField foos = new JField(
                "foos",
                "java.util.List<Foo>",
                listOfFoo,
                JVisibility.PRIVATE,
                false,
                false,
                List.of()
        );

        JType holder = new JType(
                "p",
                "Holder",
                "p.Holder",
                null,
                JTypeKind.CLASS,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                "",
                List.of(),
                List.of(),
                List.of(foos),
                List.of(new JMethod("Holder", "", null, JVisibility.PUBLIC, false, false, true, List.of(), List.of())),
                List.of()
        );

        jm.types.add(foo);
        jm.types.add(holder);

        UmlBuilder ub = new UmlBuilder();
        UmlBuilder.Result res = ub.build(jm, "TestModel", true, AssociationPolicy.RESOLVED);
        Model uml = res.umlModel;

        Path out = Files.createTempFile("j2x", ".xmi");
        XmiWriter.write(uml, jm, out);
        String xmi = Files.readString(out, StandardCharsets.UTF_8);

        assertTrue(xmi.contains("profileApplication"), "Expected a UML profileApplication");
        // Tool tags stereotype application should exist in the XMI.
        assertTrue(xmi.contains(":J2XTags"), "Expected tool tag stereotype applications in XMI");
    }
}

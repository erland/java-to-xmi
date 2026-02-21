package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorTypeRefTest {

    @Test
    void parsesArraysGenericsWildcardsOptionalAndTypeVars() throws Exception {
        Path root = Files.createTempDirectory("j2x-typeref");
        Path src = root.resolve("p");
        Files.createDirectories(src);
        Path f = src.resolve("Box.java");

        String code = "package p;\n" +
                "import java.util.List;\n" +
                "import java.util.Optional;\n" +
                "public class Box<T> {\n" +
                "  public T value;\n" +
                "  public List<String> list;\n" +
                "  public String[] arr;\n" +
                "  public Optional<Integer> opt;\n" +
                "  public List<? extends Number> nums;\n" +
                "  public void m(List<T> xs) {}\n" +
                "}\n";
        Files.writeString(f, code, StandardCharsets.UTF_8);

        JModel model = new JavaExtractor().extract(root, List.of(f));
        assertTrue(model.parseErrors.isEmpty(), "Parse errors: " + model.parseErrors);

        JType t = model.types.stream().filter(x -> "p.Box".equals(x.qualifiedName)).findFirst().orElseThrow();

        JField value = t.fields.stream().filter(x -> "value".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(value.typeRef);
        assertEquals(TypeRefKind.TYPEVAR, value.typeRef.kind);
        assertEquals("T", value.typeRef.typeVarName);

        JField list = t.fields.stream().filter(x -> "list".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(list.typeRef);
        assertEquals(TypeRefKind.PARAM, list.typeRef.kind);
        assertEquals("List", list.typeRef.simpleName);
        assertEquals(1, list.typeRef.args.size());
        assertEquals(TypeRefKind.SIMPLE, list.typeRef.args.get(0).kind);
        assertEquals("String", list.typeRef.args.get(0).simpleName);

        JField arr = t.fields.stream().filter(x -> "arr".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(arr.typeRef);
        assertEquals(TypeRefKind.ARRAY, arr.typeRef.kind);
        assertEquals(1, arr.typeRef.arrayDims);
        assertEquals(1, arr.typeRef.args.size());

        JField opt = t.fields.stream().filter(x -> "opt".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(opt.typeRef);
        assertEquals(TypeRefKind.PARAM, opt.typeRef.kind);
        assertEquals("Optional", opt.typeRef.simpleName);
        assertEquals(1, opt.typeRef.args.size());

        JField nums = t.fields.stream().filter(x -> "nums".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(nums.typeRef);
        assertEquals(TypeRefKind.PARAM, nums.typeRef.kind);
        assertEquals(1, nums.typeRef.args.size());
        assertEquals(TypeRefKind.WILDCARD, nums.typeRef.args.get(0).kind);
        assertEquals(WildcardBoundKind.EXTENDS, nums.typeRef.args.get(0).wildcardBoundKind);
        assertNotNull(nums.typeRef.args.get(0).wildcardBoundType);

        JMethod m = t.methods.stream().filter(x -> "m".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(m.params);
        assertEquals(1, m.params.size());
        assertNotNull(m.params.get(0).typeRef);
        assertEquals(TypeRefKind.PARAM, m.params.get(0).typeRef.kind);
        assertEquals("List", m.params.get(0).typeRef.simpleName);
        assertEquals(TypeRefKind.TYPEVAR, m.params.get(0).typeRef.args.get(0).kind);
    }
}

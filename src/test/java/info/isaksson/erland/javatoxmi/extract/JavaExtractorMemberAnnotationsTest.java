package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JField;
import info.isaksson.erland.javatoxmi.model.JMethod;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JParam;
import info.isaksson.erland.javatoxmi.model.JType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorMemberAnnotationsTest {

    @Test
    void extractsFieldMethodAndParameterAnnotations() throws Exception {
        Path root = Files.createTempDirectory("j2x-ann");
        Path src = root.resolve("p");
        Files.createDirectories(src);
        Path f = src.resolve("A.java");

        String code = "package p;\n" +
                "import java.util.List;\n" +
                "import jakarta.validation.constraints.NotNull;\n" +
                "import jakarta.validation.constraints.NotEmpty;\n" +
                "import jakarta.validation.constraints.Size;\n" +
                "public class A {\n" +
                "  @NotNull\n" +
                "  private String name;\n" +
                "\n" +
                "  @Size(min=1, max=5)\n" +
                "  private List<String> tags;\n" +
                "\n" +
                "  @Deprecated\n" +
                "  public void f(@NotEmpty List<String> xs) {}\n" +
                "}\n";
        Files.writeString(f, code, StandardCharsets.UTF_8);

        JModel model = new JavaExtractor().extract(root, List.of(f));
        assertTrue(model.parseErrors.isEmpty(), "Parse errors: " + model.parseErrors);

        JType t = model.types.stream().filter(x -> "p.A".equals(x.qualifiedName)).findFirst().orElseThrow();

        JField name = t.fields.stream().filter(x -> "name".equals(x.name)).findFirst().orElseThrow();
        assertNotNull(name.annotations);
        assertTrue(name.annotations.stream().anyMatch(a -> "NotNull".equals(a.simpleName)));

        JField tags = t.fields.stream().filter(x -> "tags".equals(x.name)).findFirst().orElseThrow();
        assertTrue(tags.annotations.stream().anyMatch(a -> "Size".equals(a.simpleName) && "1".equals(a.values.get("min")) && "5".equals(a.values.get("max"))));

        JMethod m = t.methods.stream().filter(x -> "f".equals(x.name)).findFirst().orElseThrow();
        assertTrue(m.annotations.stream().anyMatch(a -> "Deprecated".equals(a.simpleName)));

        JParam p = m.params.get(0);
        assertTrue(p.annotations.stream().anyMatch(a -> "NotEmpty".equals(a.simpleName)));
    }
}

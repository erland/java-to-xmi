package se.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.io.SourceScanner;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorTest {


    private static boolean listMentions(List<String> values, String token) {
        if (values == null) return false;
        String t = token.toLowerCase();
        return values.stream().filter(v -> v != null).anyMatch(v -> v.toLowerCase().contains(t));
    }

    private static boolean valueMentions(String value, String token) {
        if (value == null) return false;
        return value.toLowerCase().contains(token.toLowerCase());
    }


    @Test
    void extractsTypesAndBaselineResolution() throws Exception {
        Path source = Paths.get("samples/mini").toAbsolutePath().normalize();
        List<Path> files = SourceScanner.scan(source, List.of(), false);

        JModel model = new JavaExtractor().extract(source, files);

        // Debug output (kept intentionally lightweight) to make failures actionable when running on different machines.
        System.out.println("[DEBUG] Extracted types (" + model.types.size() + "):");
        for (JType t : model.types) {
            System.out.println("[DEBUG]  - " + t.qualifiedName + " extends=" + t.extendsType + " implements=" + t.implementsTypes);
        }
        System.out.println("[DEBUG] Unresolved types (" + model.unresolvedTypes.size() + "): " + model.unresolvedTypes);
        System.out.println("[DEBUG] Parse errors (" + model.parseErrors.size() + "): " + model.parseErrors);

        assertTrue(model.parseErrors.isEmpty(), "Expected no parse errors but got: " + model.parseErrors);
        assertTrue(model.types.size() >= 3, "Expected >= 3 types but got: " + model.types.size());

        Optional<JType> hello = model.types.stream().filter(t -> "com.example.Hello".equals(t.qualifiedName)).findFirst();
        assertTrue(hello.isPresent(), "Expected to find com.example.Hello but types were: " + model.types);
        assertTrue(
                listMentions(hello.get().implementsTypes, "Greeter"),
                "Expected Hello to implement Greeter. Actual implements list: " + hello.get().implementsTypes
        );

        Optional<JType> fancy = model.types.stream().filter(t -> "com.example.impl.FancyGreeter".equals(t.qualifiedName)).findFirst();
        assertTrue(fancy.isPresent(), "Expected to find com.example.impl.FancyGreeter but types were: " + model.types);
        assertTrue(valueMentions(fancy.get().extendsType, "Hello"), "FancyGreeter extends: " + fancy.get().extendsType);
        assertTrue(listMentions(fancy.get().implementsTypes, "Greeter"), "FancyGreeter implements: " + fancy.get().implementsTypes);

        // Baseline extractor should at least preserve the referenced type text for fields/params.
        // FancyGreeter has a field of type List<String>.
        assertTrue(
                fancy.get().fields.stream().anyMatch(f -> f.type != null && f.type.contains("List")),
                "Expected FancyGreeter to have a field referencing List but got fields: " + fancy.get().fields
        );

        // Unresolved type tracking is a best-effort baseline; allow it to vary.
        if (!model.unresolvedTypes.isEmpty()) {
            assertTrue(
                    model.unresolvedTypes.stream().allMatch(u -> u.referencedType != null && !u.referencedType.isBlank()),
                    "Expected unresolved type entries to have non-blank referencedType values but got: " + model.unresolvedTypes
            );
        }
    }
}

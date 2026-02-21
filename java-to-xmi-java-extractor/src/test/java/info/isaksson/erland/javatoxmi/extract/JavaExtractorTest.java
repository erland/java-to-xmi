package info.isaksson.erland.javatoxmi.extract;

import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JavaExtractorTest {


    
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("javaToXmi.debugTests", "false"));
    private static void debug(String s) { if (DEBUG) System.out.println(s); }

private static boolean listMentions(List<String> values, String token) {
        if (values == null) return false;
        String t = token.toLowerCase();
        return values.stream().filter(v -> v != null).anyMatch(v -> v.toLowerCase().contains(t));
    }

    private static boolean valueMentions(String value, String token) {
        if (value == null) return false;
        return value.toLowerCase().contains(token.toLowerCase());
    }

    private static Path resolveSampleDir(String name) {
        // Tests run per-module in Maven, so the working directory may be the module base dir.
        // Try common relative locations (module/, parent/, grandparent/).
        Path[] candidates = new Path[] {
                Paths.get("samples", name),
                Paths.get("..", "samples", name),
                Paths.get("..", "..", "samples", name)
        };
        for (Path p : candidates) {
            Path abs = p.toAbsolutePath().normalize();
            if (java.nio.file.Files.isDirectory(abs)) return abs;
        }
        throw new IllegalStateException("Could not locate samples/" + name + " (tried: samples, ../samples, ../../samples) from " + Paths.get("").toAbsolutePath());
    }

    @Test
    void extractsTypesAndBaselineResolution() throws Exception {
        Path source = resolveSampleDir("mini");
        List<Path> files = SourceScanner.scan(source, List.of(), false);

        JModel model = new JavaExtractor().extract(source, files);

        // Debug output (kept intentionally lightweight) to make failures actionable when running on different machines.
        debug("[DEBUG] Extracted types (" + model.types.size() + "):");
        for (JType t : model.types) {
            debug("[DEBUG]  - " + t.qualifiedName + " extends=" + t.extendsType + " implements=" + t.implementsTypes);
        }
        debug("[DEBUG] External type refs (" + model.externalTypeRefs.size() + "): " + model.externalTypeRefs);
        debug("[DEBUG] Unresolved (unknown) types (" + model.unresolvedTypes.size() + "): " + model.unresolvedTypes);
        debug("[DEBUG] Parse errors (" + model.parseErrors.size() + "): " + model.parseErrors);

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

        // External + unresolved tracking is best-effort baseline; allow it to vary.
        for (var u : model.externalTypeRefs) {
            assertNotNull(u.referencedType);
            assertFalse(u.referencedType.isBlank());
        }
        for (var u : model.unresolvedTypes) {
            assertNotNull(u.referencedType);
            assertFalse(u.referencedType.isBlank());
        }
    }
}

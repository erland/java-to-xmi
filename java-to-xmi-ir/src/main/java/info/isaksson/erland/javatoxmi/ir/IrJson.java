package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON serialization utilities for the cross-language IR.
 *
 * <p>Writing is deterministic: we normalize list ordering and enable stable map/key ordering.</p>
 */
public final class IrJson {

    private static final ObjectMapper MAPPER = createMapper();
    private static final DefaultPrettyPrinter PRETTY = createPrettyPrinter();

    private IrJson() {}

    public static IrModel read(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path is null");
        try (var in = Files.newInputStream(path)) {
            return MAPPER.readValue(in, IrModel.class);
        }
    }

    /** Parse an IR model from a JSON string. */
    public static IrModel readFromString(String json) throws IOException {
        if (json == null) throw new IllegalArgumentException("json is null");
        return MAPPER.readValue(json, IrModel.class);
    }

    public static void write(IrModel model, Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path is null");
        IrModel normalized = IrNormalizer.normalize(model);
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        try (var out = Files.newOutputStream(path)) {
            MAPPER.writer(PRETTY).writeValue(out, normalized);
            // Ensure trailing newline for diff-friendliness.
            out.write('\n');
        }
    }

    public static String toJsonString(IrModel model) throws IOException {
        IrModel normalized = IrNormalizer.normalize(model);
        return MAPPER.writer(PRETTY).writeValueAsString(normalized) + "\n";
    }

    private static ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        // Prevent Jackson from closing the provided OutputStream/Writer.
        om.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        om.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        // Keep output compact and stable.
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    private static DefaultPrettyPrinter createPrettyPrinter() {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        pp.indentObjectsWith(indenter);
        pp.indentArraysWith(indenter);
        return pp;
    }
}

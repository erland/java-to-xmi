package info.isaksson.erland.javatoxmi.emitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects warnings during emission.
 *
 * <p>Warnings are deterministic: final output is sorted by (code, message, contextString).</p>
 */
public final class EmitterWarnings {

    private final List<EmitterWarning> warnings = new ArrayList<>();

    public void warn(String code, String message) {
        warn(code, message, null);
    }

    public void warn(String code, String message, Map<String, String> context) {
        warnings.add(new EmitterWarning(code, message, context == null ? Collections.emptyMap() : context));
    }

    public void warn(String code, String message, String k1, String v1) {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put(k1, v1);
        warn(code, message, ctx);
    }

    public void warn(String code, String message, String k1, String v1, String k2, String v2) {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put(k1, v1);
        ctx.put(k2, v2);
        warn(code, message, ctx);
    }

    public List<EmitterWarning> toDeterministicList() {
        List<EmitterWarning> out = new ArrayList<>(warnings);
        out.sort(Comparator
                .comparing((EmitterWarning w) -> w.code)
                .thenComparing(w -> w.message)
                .thenComparing(w -> contextString(w.context)));
        return Collections.unmodifiableList(out);
    }

    private static String contextString(Map<String, String> ctx) {
        if (ctx == null || ctx.isEmpty()) return "";
        // stable serialization: key-sorted
        List<String> keys = new ArrayList<>(ctx.keySet());
        keys.sort(String::compareTo);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k).append('=').append(ctx.get(k)).append(';');
        }
        return sb.toString();
    }
}

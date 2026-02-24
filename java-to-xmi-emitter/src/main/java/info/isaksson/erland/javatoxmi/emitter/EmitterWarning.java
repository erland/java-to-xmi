package info.isaksson.erland.javatoxmi.emitter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A non-fatal deterministic warning produced during emission. */
public final class EmitterWarning {

    /** Warning code stable across versions. */
    public final String code;

    /** Human-readable message. */
    public final String message;

    /** Optional structured context (stable keys recommended). */
    public final Map<String, String> context;

    public EmitterWarning(String code, String message, Map<String, String> context) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        if (context == null || context.isEmpty()) {
            this.context = Collections.emptyMap();
        } else {
            this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
        }
    }
}

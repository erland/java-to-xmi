package info.isaksson.erland.javatoxmi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime semantics applied directly to a UML element (classifier/operation/package/artifact).
 *
 * <p>This is intentionally lightweight: the extractor identifies a target key and attaches a
 * stereotype name plus tagged values. The UML builder annotates the corresponding UML element
 * with {@code java-to-xmi:runtime} + {@code java-to-xmi:tags}, and the XMI writer post-processes
 * these into proper stereotype applications.</p>
 */
public final class JRuntimeAnnotation {

    /**
     * Target key for resolving to a UML element.
     *
     * <ul>
     *   <li>Classifier: fully qualified name, e.g. {@code com.example.ApiResource}</li>
     *   <li>Operation: {@code <typeQName>#<methodName>(<paramTypes...>)} using raw param type strings</li>
     * </ul>
     */
    public final String targetKey;

    /** Simple stereotype name (no profile prefix), e.g. {@code RestResource}. */
    public final String stereotype;

    /** Tagged values (deterministic key order preserved by using LinkedHashMap). */
    public final Map<String, String> tags;

    public JRuntimeAnnotation(String targetKey, String stereotype, Map<String, String> tags) {
        this.targetKey = Objects.requireNonNullElse(targetKey, "");
        this.stereotype = (stereotype == null || stereotype.isBlank()) ? null : stereotype.trim();
        this.tags = tags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tags);
    }
}

package se.erland.javatoxmi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single Java annotation usage (e.g. {@code @Entity}, {@code @Table(name="person")}).
 *
 * <p>MVP note: values are normalised to strings by the extractor. More structured
 * representations (arrays, nested annotations, etc.) can be introduced later.</p>
 */
public final class JAnnotationUse {
    /** Simple name, e.g. {@code Entity}. */
    public final String simpleName;

    /** Qualified name if resolvable/known, e.g. {@code jakarta.persistence.Entity}. */
    public final String qualifiedName;

    /**
     * Annotation member values, keyed by member name.
     *
     * <p>For single-member annotations, the extractor should use the key {@code value}.</p>
     */
    public final Map<String, String> values;

    public JAnnotationUse(String simpleName, String qualifiedName, Map<String, String> values) {
        this.simpleName = Objects.requireNonNullElse(simpleName, "");
        this.qualifiedName = (qualifiedName == null || qualifiedName.isBlank()) ? null : qualifiedName;
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
}

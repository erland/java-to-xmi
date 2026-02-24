package info.isaksson.erland.javatoxmi.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a non-structural / runtime semantic relation.
 *
 * <p>Examples: CDI event fire/observe edges, REST endpoint exposure metadata, interceptor bindings,
 * messaging consumer/producer semantics, Flyway migration ownership, JPMS module requires edges.</p>
 *
 * <p>These relations are designed to be emitted as UML dependencies (or related UML elements)
 * with a stereotype (role) and tagged values (metadata).</p>
 */
public final class JRuntimeRelation {
    /** Optional stable id (e.g. from IR relation id). */
    public final String id;

    /** Source qualified name (Java-style qname used by this project). */
    public final String sourceQualifiedName;

    /** Target qualified name (Java-style qname used by this project). */
    public final String targetQualifiedName;

    /** Optional display name for the emitted UML relation. */
    public final String name;

    /** Optional stereotype name to apply (e.g. "FiresEvent"). */
    public final String stereotype;

    /** Free-form metadata tags (persisted deterministically). */
    public final Map<String, String> tags;

    public JRuntimeRelation(String id,
                            String sourceQualifiedName,
                            String targetQualifiedName,
                            String name,
                            String stereotype,
                            Map<String, String> tags) {
        this.id = id;
        this.sourceQualifiedName = sourceQualifiedName;
        this.targetQualifiedName = targetQualifiedName;
        this.name = name;
        this.stereotype = stereotype;
        if (tags == null || tags.isEmpty()) {
            this.tags = Collections.emptyMap();
        } else {
            this.tags = Collections.unmodifiableMap(new HashMap<>(tags));
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JRuntimeRelation)) return false;
        JRuntimeRelation that = (JRuntimeRelation) o;
        return Objects.equals(id, that.id)
                && Objects.equals(sourceQualifiedName, that.sourceQualifiedName)
                && Objects.equals(targetQualifiedName, that.targetQualifiedName)
                && Objects.equals(name, that.name)
                && Objects.equals(stereotype, that.stereotype)
                && Objects.equals(tags, that.tags);
    }

    @Override public int hashCode() {
        return Objects.hash(id, sourceQualifiedName, targetQualifiedName, name, stereotype, tags);
    }

    @Override public String toString() {
        return "JRuntimeRelation{" +
                "id='" + id + '\'' +
                ", source='" + sourceQualifiedName + '\'' +
                ", target='" + targetQualifiedName + '\'' +
                ", stereotype='" + stereotype + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + tags +
                '}';
    }
}

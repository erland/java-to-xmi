package info.isaksson.erland.javatoxmi.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A database migration artifact (e.g. Flyway V1__init.sql).
 *
 * <p>This is represented in UML as an {@code Artifact} with the {@code FlywayMigration}
 * runtime stereotype marker and tagged values.</p>
 */
public final class JMigrationArtifact {
    public enum Kind { VERSIONED, REPEATABLE, JAVA }

    /** Stable id for determinism (used for dependency id / artifact id). */
    public final String id;

    /** Optional version (e.g. 1, 2.1). Null for repeatable/java migrations where unknown. */
    public final String version;

    /** Human readable description (best-effort). */
    public final String description;

    /** Path relative to source root (for SQL/resource migrations) or qualified name (for JAVA). */
    public final String path;

    /** sql or java. */
    public final String type;

    public final Kind kind;

    /** Extra tags for future extensions. */
    public final Map<String, String> tags = new LinkedHashMap<>();

    public JMigrationArtifact(String id, String version, String description, String path, String type, Kind kind) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = version;
        this.description = description == null ? "" : description;
        this.path = Objects.requireNonNull(path, "path");
        this.type = type == null ? "sql" : type;
        this.kind = kind == null ? Kind.VERSIONED : kind;
    }
}

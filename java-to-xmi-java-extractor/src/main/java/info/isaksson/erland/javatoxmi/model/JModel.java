package info.isaksson.erland.javatoxmi.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JModel {
    public final Path sourceRoot;
    public final List<Path> sourceFiles;

    public final List<JType> types = new ArrayList<>();
    public final List<String> parseErrors = new ArrayList<>();
    public final List<UnresolvedTypeRef> externalTypeRefs = new ArrayList<>();
    /** Truly unresolved (cannot be qualified even as an external stub). */
    public final List<UnresolvedTypeRef> unresolvedTypes = new ArrayList<>();

    /**
     * Non-structural/runtime relations (REST endpoints, CDI events, messaging, migrations, JPMS module edges, etc.).
     *
     * <p>These are emitted as stereotyped UML dependencies/artifacts/packages and can be used by consumers
     * (e.g. an EA modeller) to browse runtime semantics that are not expressible as fields/method signatures alone.</p>
     */
    public final List<JRuntimeRelation> runtimeRelations = new ArrayList<>();

    /**
     * Runtime semantics applied directly to existing UML elements (e.g. REST resources/operations).
     *
     * <p>These are emitted as {@code java-to-xmi:runtime} annotations and later post-processed into
     * stereotype applications during XMI writing. This avoids relying on UML2 stereotype application
     * at build-time while still preserving semantics.</p>
     */
    public final List<JRuntimeAnnotation> runtimeAnnotations = new ArrayList<>();

    /** Flyway-style DB migration artifacts discovered in resources. */
    
    /** JPMS module boundaries extracted from module-info.java. */
    public final List<JJavaModule> javaModules = new ArrayList<>();

public final List<JMigrationArtifact> migrationArtifacts = new ArrayList<>();

    public JModel(Path sourceRoot, List<Path> sourceFiles) {
        this.sourceRoot = sourceRoot;
        this.sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }
}

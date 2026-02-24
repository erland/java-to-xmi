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

    public JModel(Path sourceRoot, List<Path> sourceFiles) {
        this.sourceRoot = sourceRoot;
        this.sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }
}

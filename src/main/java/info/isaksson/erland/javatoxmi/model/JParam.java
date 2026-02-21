package info.isaksson.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JParam {
    public final String name;
    public final String type; // may be unresolved; keep as string
    /** Parsed structural representation of {@link #type}. May be null until populated by extractor. */
    public final TypeRef typeRef;
    /** Java annotations on the parameter. Empty if not extracted. */
    public final List<JAnnotationUse> annotations;

    public JParam(String name, String type) {
        this(name, type, null, List.of());
    }

    public JParam(String name, String type, TypeRef typeRef, List<JAnnotationUse> annotations) {
        this.name = Objects.requireNonNullElse(name, "");
        this.type = Objects.requireNonNullElse(type, "java.lang.Object");
        this.typeRef = typeRef;
        this.annotations = annotations == null ? List.of() : List.copyOf(new ArrayList<>(annotations));
    }
}

package info.isaksson.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JField {
    public final String name;
    public final String type; // may be unresolved
    /** Parsed structural representation of {@link #type}. May be null until populated by extractor. */
    public final TypeRef typeRef;
    public final JVisibility visibility;
    public final boolean isStatic;
    public final boolean isFinal;
    /** Java annotations on the field. Empty if not extracted. */
    public final List<JAnnotationUse> annotations;

    public JField(String name, String type, JVisibility visibility, boolean isStatic, boolean isFinal) {
        this(name, type, null, visibility, isStatic, isFinal, List.of());
    }

    public JField(String name,
                  String type,
                  TypeRef typeRef,
                  JVisibility visibility,
                  boolean isStatic,
                  boolean isFinal,
                  List<JAnnotationUse> annotations) {
        this.name = Objects.requireNonNullElse(name, "");
        this.type = Objects.requireNonNullElse(type, "java.lang.Object");
        this.typeRef = typeRef;
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
        this.annotations = annotations == null ? List.of() : List.copyOf(new ArrayList<>(annotations));
    }
}

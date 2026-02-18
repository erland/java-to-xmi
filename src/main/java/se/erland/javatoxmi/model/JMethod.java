package se.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JMethod {
    public final String name;
    public final String returnType; // constructors may use empty string
    /** Parsed structural representation of {@link #returnType}. May be null until populated by extractor. */
    public final TypeRef returnTypeRef;
    public final JVisibility visibility;
    public final boolean isStatic;
    public final boolean isAbstract;
    public final boolean isConstructor;
    public final List<JParam> params;
    /** Java annotations on the method/constructor. Empty if not extracted. */
    public final List<JAnnotationUse> annotations;

    public JMethod(String name,
                   String returnType,
                   JVisibility visibility,
                   boolean isStatic,
                   boolean isAbstract,
                   boolean isConstructor,
                   List<JParam> params) {
        this(name, returnType, null, visibility, isStatic, isAbstract, isConstructor, params, List.of());
    }

    public JMethod(String name,
                   String returnType,
                   TypeRef returnTypeRef,
                   JVisibility visibility,
                   boolean isStatic,
                   boolean isAbstract,
                   boolean isConstructor,
                   List<JParam> params,
                   List<JAnnotationUse> annotations) {
        this.name = Objects.requireNonNullElse(name, "");
        this.returnType = Objects.requireNonNullElse(returnType, "");
        this.returnTypeRef = returnTypeRef;
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
        this.isConstructor = isConstructor;
        this.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
        this.annotations = annotations == null ? List.of() : List.copyOf(new ArrayList<>(annotations));
    }
}

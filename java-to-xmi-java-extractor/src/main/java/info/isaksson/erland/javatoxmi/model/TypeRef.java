package info.isaksson.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A lightweight, best-effort structural representation of a Java type.
 *
 * <p>Designed for incremental enrichment: start with {@link #raw} + {@link #kind}
 * (and perhaps {@link #simpleName}), then later refine with {@link #qnameHint},
 * generic {@link #args}, array dimensions, and wildcard/type-variable details.</p>
 */
public final class TypeRef {
    /** Original pretty-printed type string, e.g. {@code List<Foo[]>}. */
    public final String raw;
    public final TypeRefKind kind;

    /** Simple name portion (best effort), e.g. {@code List} in {@code java.util.List}. */
    public final String simpleName;
    /** Best-effort qualified name hint, e.g. {@code java.util.List}. May be empty. */
    public final String qnameHint;

    /** Generic type arguments for {@link TypeRefKind#PARAM}. */
    public final List<TypeRef> args;

    /** Array dimensions for {@link TypeRefKind#ARRAY}. */
    public final int arrayDims;

    /** Type variable name for {@link TypeRefKind#TYPEVAR}. */
    public final String typeVarName;

    /** Wildcard bound kind for {@link TypeRefKind#WILDCARD}. */
    public final WildcardBoundKind wildcardBoundKind;
    /** Wildcard bound type for bounded wildcards. Null for {@link WildcardBoundKind#UNBOUNDED}. */
    public final TypeRef wildcardBoundType;

    public TypeRef(String raw,
                   TypeRefKind kind,
                   String simpleName,
                   String qnameHint,
                   List<TypeRef> args,
                   int arrayDims,
                   String typeVarName,
                   WildcardBoundKind wildcardBoundKind,
                   TypeRef wildcardBoundType) {
        this.raw = Objects.requireNonNullElse(raw, "");
        this.kind = kind == null ? TypeRefKind.SIMPLE : kind;
        this.simpleName = Objects.requireNonNullElse(simpleName, "");
        this.qnameHint = Objects.requireNonNullElse(qnameHint, "");
        this.args = args == null ? List.of() : List.copyOf(new ArrayList<>(args));
        this.arrayDims = Math.max(0, arrayDims);
        this.typeVarName = Objects.requireNonNullElse(typeVarName, "");
        this.wildcardBoundKind = wildcardBoundKind == null ? WildcardBoundKind.UNBOUNDED : wildcardBoundKind;
        this.wildcardBoundType = wildcardBoundType;
    }

    public static TypeRef simple(String raw, String simpleName, String qnameHint) {
        return new TypeRef(raw, TypeRefKind.SIMPLE, simpleName, qnameHint, List.of(), 0, "",
                WildcardBoundKind.UNBOUNDED, null);
    }

    public static TypeRef array(String raw, TypeRef componentType, int dims) {
        // store component as a single arg for convenience
        List<TypeRef> a = componentType == null ? List.of() : List.of(componentType);
        return new TypeRef(raw, TypeRefKind.ARRAY, "", "", a, dims, "",
                WildcardBoundKind.UNBOUNDED, null);
    }

    public static TypeRef param(String raw, String simpleName, String qnameHint, List<TypeRef> args) {
        return new TypeRef(raw, TypeRefKind.PARAM, simpleName, qnameHint, args, 0, "",
                WildcardBoundKind.UNBOUNDED, null);
    }

    public static TypeRef typeVar(String raw, String name) {
        return new TypeRef(raw, TypeRefKind.TYPEVAR, "", "", List.of(), 0, name,
                WildcardBoundKind.UNBOUNDED, null);
    }

    public static TypeRef wildcard(String raw, WildcardBoundKind boundKind, TypeRef boundType) {
        return new TypeRef(raw, TypeRefKind.WILDCARD, "", "", List.of(), 0, "",
                boundKind, boundType);
    }
}

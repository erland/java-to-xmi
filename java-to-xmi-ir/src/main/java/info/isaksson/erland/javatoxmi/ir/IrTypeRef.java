package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Language-agnostic type reference.
 *
 * <p>For complex languages, store additional details using taggedValues.</p>
 */
@JsonPropertyOrder({"kind","name","typeArgs","elementType","taggedValues"})
public final class IrTypeRef {
    public final IrTypeRefKind kind;

    /** For NAMED/PRIMITIVE/GENERIC kinds. */
    public final String name;

    /** For GENERIC: arguments. For UNION/INTERSECTION: variants. */
    public final List<IrTypeRef> typeArgs;

    /** For ARRAY: element type. */
    public final IrTypeRef elementType;

    /** Optional tagged values for language-specific details (e.g., TS "readonly", "optional"). */
    public final List<IrTaggedValue> taggedValues;

    @JsonCreator
    public IrTypeRef(
            
            @JsonProperty("kind") IrTypeRefKind kind,
            @JsonProperty("name") String name,
            @JsonProperty("typeArgs") List<IrTypeRef> typeArgs,
            @JsonProperty("elementType") IrTypeRef elementType,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues
    ) {
        this.kind = kind == null ? IrTypeRefKind.UNKNOWN : kind;
        this.name = name;
        this.typeArgs = typeArgs == null ? List.of() : List.copyOf(typeArgs);
        this.elementType = elementType;
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
    }

    public static IrTypeRef unknown() {
        return new IrTypeRef(IrTypeRefKind.UNKNOWN, null, null, null, null);
    }

    public static IrTypeRef named(String qualifiedOrSimpleName) {
        return new IrTypeRef(IrTypeRefKind.NAMED, qualifiedOrSimpleName, null, null, null);
    }

    public static IrTypeRef primitive(String name) {
        return new IrTypeRef(IrTypeRefKind.PRIMITIVE, name, null, null, null);
    }

    /** Convenience for operations without explicit return type. */
    public static IrTypeRef voidType() {
        return primitive("void");
    }

    public static IrTypeRef arrayOf(IrTypeRef element) {
        return new IrTypeRef(IrTypeRefKind.ARRAY, null, null, element, null);
    }

    public static IrTypeRef generic(String rawName, List<IrTypeRef> args) {
        return new IrTypeRef(IrTypeRefKind.GENERIC, rawName, args, null, null);
    }

    public static IrTypeRef union(List<IrTypeRef> variants) {
        return new IrTypeRef(IrTypeRefKind.UNION, null, variants, null, null);
    }

    public static IrTypeRef intersection(List<IrTypeRef> variants) {
        return new IrTypeRef(IrTypeRefKind.INTERSECTION, null, variants, null, null);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrTypeRef)) return false;
        IrTypeRef that = (IrTypeRef) o;
        return kind == that.kind &&
                Objects.equals(name, that.name) &&
                Objects.equals(typeArgs, that.typeArgs) &&
                Objects.equals(elementType, that.elementType) &&
                Objects.equals(taggedValues, that.taggedValues);
    }

    @Override public int hashCode() {
        return Objects.hash(kind, name, typeArgs, elementType, taggedValues);
    }

    @Override public String toString() {
        if (kind == IrTypeRefKind.ARRAY) return elementType + "[]";
        if (kind == IrTypeRefKind.GENERIC) return name + "<" + typeArgs + ">";
        if (kind == IrTypeRefKind.NAMED || kind == IrTypeRefKind.PRIMITIVE) return String.valueOf(name);
        return kind.name();
    }
}

package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * A stereotype name (optionally qualified). Keep it free-form to avoid coupling to any one UML profile.
 */
@JsonPropertyOrder({"name","qualifiedName"})
public final class IrStereotype {
    public final String name;
    public final String qualifiedName;

    @JsonCreator
    public IrStereotype(
            @JsonProperty("name") String name,
            @JsonProperty("qualifiedName") String qualifiedName
    ) {
        this.name = name;
        this.qualifiedName = qualifiedName;
    }

    public static IrStereotype simple(String name) {
        return new IrStereotype(name, null);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrStereotype)) return false;
        IrStereotype that = (IrStereotype) o;
        return Objects.equals(name, that.name) && Objects.equals(qualifiedName, that.qualifiedName);
    }

    @Override public int hashCode() {
        return Objects.hash(name, qualifiedName);
    }

    @Override public String toString() {
        return qualifiedName == null ? name : qualifiedName;
    }
}

package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"name","type","isMulti"})
public final class IrStereotypePropertyDefinition {

    public enum Type {
        STRING, BOOLEAN, INTEGER, NUMBER;

        public static Type fromJson(String raw) {
            if (raw == null) return STRING;
            try {
                return Type.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return STRING;
            }
        }

        public String toJson() {
            return name().toLowerCase();
        }
    }

    public final String name;

    /** Primitive type name (string/boolean/integer/number). */
    public final String type;

    public final boolean isMulti;

    @JsonCreator
    public IrStereotypePropertyDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("isMulti") boolean isMulti
    ) {
        this.name = name;
        this.type = type == null ? Type.STRING.toJson() : type;
        this.isMulti = isMulti;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrStereotypePropertyDefinition)) return false;
        IrStereotypePropertyDefinition that = (IrStereotypePropertyDefinition) o;
        return isMulti == that.isMulti &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type);
    }

    @Override public int hashCode() {
        return Objects.hash(name, type, isMulti);
    }
}

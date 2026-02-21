package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"name","type"})
public final class IrParameter {
    public final String name;
    public final IrTypeRef type;
    public final List<IrTaggedValue> taggedValues;

    @JsonCreator
    public IrParameter(
            @JsonProperty("name") String name,
            @JsonProperty("type") IrTypeRef type,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues
    ) {
        this.name = name;
        this.type = type == null ? IrTypeRef.unknown() : type;
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrParameter)) return false;
        IrParameter that = (IrParameter) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(taggedValues, that.taggedValues);
    }

    @Override public int hashCode() {
        return Objects.hash(name, type, taggedValues);
    }
}

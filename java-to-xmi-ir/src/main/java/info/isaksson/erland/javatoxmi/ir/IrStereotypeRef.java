package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;

/**
 * Reference to a stereotype definition by id, optionally with tagged values (values).
 */
@JsonPropertyOrder({"stereotypeId","values"})
public final class IrStereotypeRef {

    public final String stereotypeId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final Map<String, Object> values;

    @JsonCreator
    public IrStereotypeRef(
            @JsonProperty("stereotypeId") String stereotypeId,
            @JsonProperty("values") Map<String, Object> values
    ) {
        this.stereotypeId = stereotypeId;
        this.values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static IrStereotypeRef of(String stereotypeId) {
        return new IrStereotypeRef(stereotypeId, null);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrStereotypeRef)) return false;
        IrStereotypeRef that = (IrStereotypeRef) o;
        return Objects.equals(stereotypeId, that.stereotypeId) &&
                Objects.equals(values, that.values);
    }

    @Override public int hashCode() {
        return Objects.hash(stereotypeId, values);
    }
}

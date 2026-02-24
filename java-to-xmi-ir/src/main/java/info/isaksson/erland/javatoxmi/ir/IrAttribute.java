package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"id","name","visibility","isStatic","isFinal","type","stereotypes","stereotypeRefs","taggedValues","source"})
public final class IrAttribute {
    public final String id;
    public final String name;
    public final IrVisibility visibility;
    public final boolean isStatic;
    public final boolean isFinal;
    public final IrTypeRef type;
    public final List<IrStereotype> stereotypes;
    public final List<IrTaggedValue> taggedValues;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)


    public final List<IrStereotypeRef> stereotypeRefs;
    public final IrSourceRef source;

    @JsonCreator
    public IrAttribute(
            
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("visibility") IrVisibility visibility,
            @JsonProperty("isStatic") boolean isStatic,
            @JsonProperty("isFinal") boolean isFinal,
            @JsonProperty("type") IrTypeRef type,
            @JsonProperty("stereotypes") List<IrStereotype> stereotypes,
            @JsonProperty("stereotypeRefs") List<IrStereotypeRef> stereotypeRefs,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues,
            @JsonProperty("source") IrSourceRef source
    ) {
        this.id = id;
        this.name = name;
        this.visibility = visibility == null ? IrVisibility.PACKAGE : visibility;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
        this.type = type == null ? IrTypeRef.unknown() : type;
        this.stereotypes = stereotypes == null ? List.of() : List.copyOf(stereotypes);
        this.stereotypeRefs = stereotypeRefs == null ? List.of() : List.copyOf(stereotypeRefs);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
        this.source = source;
    }

    
    /** Backwards-compatible constructor (without stereotypeRefs). */
    public IrAttribute(
            String id,
            String name,
            IrVisibility visibility,
            boolean isStatic,
            boolean isFinal,
            IrTypeRef type,
            List<IrStereotype> stereotypes,
            List<IrTaggedValue> taggedValues,
            IrSourceRef source
    ) {
        this(id, name, visibility, isStatic, isFinal, type, stereotypes, null, taggedValues, source);
    }

@Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrAttribute)) return false;
        IrAttribute that = (IrAttribute) o;
        return isStatic == that.isStatic &&
                isFinal == that.isFinal &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                visibility == that.visibility &&
                Objects.equals(type, that.type) &&
                Objects.equals(stereotypes, that.stereotypes) &&
                Objects.equals(stereotypeRefs, that.stereotypeRefs) &&
                Objects.equals(taggedValues, that.taggedValues) &&
                Objects.equals(source, that.source);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, visibility, isStatic, isFinal, type, stereotypes, stereotypeRefs, taggedValues, source);
    }
}
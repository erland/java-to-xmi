package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"id","kind","sourceId","targetId","name","stereotypes","stereotypeRefs","taggedValues","source"})
public final class IrRelation {
    public final String id;
    public final IrRelationKind kind;
    public final String sourceId;
    public final String targetId;
    public final String name;

    public final List<IrStereotype> stereotypes;
    public final List<IrTaggedValue> taggedValues;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)


    public final List<IrStereotypeRef> stereotypeRefs;
    public final IrSourceRef source;

    @JsonCreator
    public IrRelation(
            
            @JsonProperty("id") String id,
            @JsonProperty("kind") IrRelationKind kind,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("targetId") String targetId,
            @JsonProperty("name") String name,
            @JsonProperty("stereotypes") List<IrStereotype> stereotypes,
            @JsonProperty("stereotypeRefs") List<IrStereotypeRef> stereotypeRefs,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues,
            @JsonProperty("source") IrSourceRef source
    ) {
        this.id = id;
        this.kind = kind == null ? IrRelationKind.DEPENDENCY : kind;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.name = name;
        this.stereotypes = stereotypes == null ? List.of() : List.copyOf(stereotypes);
        this.stereotypeRefs = stereotypeRefs == null ? List.of() : List.copyOf(stereotypeRefs);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
        this.source = source;
    }

    
    /** Backwards-compatible constructor (without stereotypeRefs). */
    public IrRelation(
            String id,
            IrRelationKind kind,
            String sourceId,
            String targetId,
            String name,
            List<IrStereotype> stereotypes,
            List<IrTaggedValue> taggedValues,
            IrSourceRef source
    ) {
        this(id, kind, sourceId, targetId, name, stereotypes, null, taggedValues, source);
    }

@Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrRelation)) return false;
        IrRelation that = (IrRelation) o;
        return Objects.equals(id, that.id) &&
                kind == that.kind &&
                Objects.equals(sourceId, that.sourceId) &&
                Objects.equals(targetId, that.targetId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(stereotypes, that.stereotypes) &&
                Objects.equals(stereotypeRefs, that.stereotypeRefs) &&
                Objects.equals(taggedValues, that.taggedValues) &&
                Objects.equals(source, that.source);
    }

    @Override public int hashCode() {
        return Objects.hash(id, kind, sourceId, targetId, name, stereotypes, stereotypeRefs, taggedValues, source);
    }
}
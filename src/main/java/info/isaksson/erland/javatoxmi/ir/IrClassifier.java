package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"id","name","qualifiedName","packageId","kind","visibility","attributes","operations","stereotypes","taggedValues","source"})
public final class IrClassifier {
    public final String id;
    public final String name;
    public final String qualifiedName;
    public final String packageId;
    public final IrClassifierKind kind;
    public final IrVisibility visibility;

    public final List<IrAttribute> attributes;
    public final List<IrOperation> operations;

    public final List<IrStereotype> stereotypes;
    public final List<IrTaggedValue> taggedValues;

    public final IrSourceRef source;

    @JsonCreator
    public IrClassifier(
            
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("qualifiedName") String qualifiedName,
            @JsonProperty("packageId") String packageId,
            @JsonProperty("kind") IrClassifierKind kind,
            @JsonProperty("visibility") IrVisibility visibility,
            @JsonProperty("attributes") List<IrAttribute> attributes,
            @JsonProperty("operations") List<IrOperation> operations,
            @JsonProperty("stereotypes") List<IrStereotype> stereotypes,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues,
            @JsonProperty("source") IrSourceRef source
    ) {
        this.id = id;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.packageId = packageId;
        this.kind = kind == null ? IrClassifierKind.CLASS : kind;
        this.visibility = visibility == null ? IrVisibility.PACKAGE : visibility;
        this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
        this.operations = operations == null ? List.of() : List.copyOf(operations);
        this.stereotypes = stereotypes == null ? List.of() : List.copyOf(stereotypes);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
        this.source = source;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrClassifier)) return false;
        IrClassifier that = (IrClassifier) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(qualifiedName, that.qualifiedName) &&
                Objects.equals(packageId, that.packageId) &&
                kind == that.kind &&
                visibility == that.visibility &&
                Objects.equals(attributes, that.attributes) &&
                Objects.equals(operations, that.operations) &&
                Objects.equals(stereotypes, that.stereotypes) &&
                Objects.equals(taggedValues, that.taggedValues) &&
                Objects.equals(source, that.source);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, qualifiedName, packageId, kind, visibility, attributes, operations, stereotypes, taggedValues, source);
    }
}

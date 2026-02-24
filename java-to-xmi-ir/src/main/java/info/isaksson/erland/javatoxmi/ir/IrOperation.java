package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"id","name","visibility","isStatic","isAbstract","isConstructor","returnType","parameters","stereotypes","stereotypeRefs","taggedValues","source"})
public final class IrOperation {
    public final String id;
    public final String name;
    public final IrVisibility visibility;
    public final boolean isStatic;
    public final boolean isAbstract;
    public final boolean isConstructor;
    public final IrTypeRef returnType;
    public final List<IrParameter> parameters;

    /** Legacy stereotype list (name/qualifiedName). */
    public final List<IrStereotype> stereotypes;

    /** Registry-based stereotypes by id + optional tagged values. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)

    public final List<IrStereotypeRef> stereotypeRefs;

    public final List<IrTaggedValue> taggedValues;
    public final IrSourceRef source;

    @JsonCreator
    public IrOperation(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("visibility") IrVisibility visibility,
            @JsonProperty("isStatic") boolean isStatic,
            @JsonProperty("isAbstract") boolean isAbstract,
            @JsonProperty("isConstructor") boolean isConstructor,
            @JsonProperty("returnType") IrTypeRef returnType,
            @JsonProperty("parameters") List<IrParameter> parameters,
            @JsonProperty("stereotypes") List<IrStereotype> stereotypes,
            @JsonProperty("stereotypeRefs") List<IrStereotypeRef> stereotypeRefs,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues,
            @JsonProperty("source") IrSourceRef source
    ) {
        this.id = id;
        this.name = name;
        this.visibility = visibility == null ? IrVisibility.PACKAGE : visibility;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
        this.isConstructor = isConstructor;
        this.returnType = returnType == null ? IrTypeRef.voidType() : returnType;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.stereotypes = stereotypes == null ? List.of() : List.copyOf(stereotypes);
        this.stereotypeRefs = stereotypeRefs == null ? List.of() : List.copyOf(stereotypeRefs);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
        this.source = source;
    }

    /** Backwards-compatible constructor (without stereotypeRefs). */
    public IrOperation(
            String id,
            String name,
            IrVisibility visibility,
            boolean isStatic,
            boolean isAbstract,
            boolean isConstructor,
            IrTypeRef returnType,
            List<IrParameter> parameters,
            List<IrStereotype> stereotypes,
            List<IrTaggedValue> taggedValues,
            IrSourceRef source
    ) {
        this(id, name, visibility, isStatic, isAbstract, isConstructor, returnType, parameters, stereotypes, null, taggedValues, source);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrOperation)) return false;
        IrOperation that = (IrOperation) o;
        return isStatic == that.isStatic &&
                isAbstract == that.isAbstract &&
                isConstructor == that.isConstructor &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                visibility == that.visibility &&
                Objects.equals(returnType, that.returnType) &&
                Objects.equals(parameters, that.parameters) &&
                Objects.equals(stereotypes, that.stereotypes) &&
                Objects.equals(stereotypeRefs, that.stereotypeRefs) &&
                Objects.equals(taggedValues, that.taggedValues) &&
                Objects.equals(source, that.source);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, visibility, isStatic, isAbstract, isConstructor, returnType, parameters, stereotypes, stereotypeRefs, taggedValues, source);
    }
}
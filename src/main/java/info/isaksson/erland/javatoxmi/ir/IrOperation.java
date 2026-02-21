package info.isaksson.erland.javatoxmi.ir;

import java.util.List;
import java.util.Objects;

public final class IrOperation {
    public final String id;
    public final String name;
    public final IrVisibility visibility;
    public final boolean isStatic;
    public final boolean isAbstract;
    public final boolean isConstructor;
    public final IrTypeRef returnType;
    public final List<IrParameter> parameters;
    public final List<IrStereotype> stereotypes;
    public final List<IrTaggedValue> taggedValues;
    public final IrSourceRef source;

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
        this.id = id;
        this.name = name;
        this.visibility = visibility == null ? IrVisibility.PACKAGE : visibility;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
        this.isConstructor = isConstructor;
        this.returnType = returnType == null ? IrTypeRef.unknown() : returnType;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.stereotypes = stereotypes == null ? List.of() : List.copyOf(stereotypes);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
        this.source = source;
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
                Objects.equals(taggedValues, that.taggedValues) &&
                Objects.equals(source, that.source);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, visibility, isStatic, isAbstract, isConstructor, returnType, parameters, stereotypes, taggedValues, source);
    }
}

package info.isaksson.erland.javatoxmi.ir;

import java.util.List;
import java.util.Objects;

/**
 * Optional packaging structure. For languages with modules/folders, use packages to group classifiers.
 */
public final class IrPackage {
    public final String id;
    public final String name;
    public final String qualifiedName;
    public final String parentId;
    public final List<IrTaggedValue> taggedValues;

    public IrPackage(String id, String name, String qualifiedName, String parentId, List<IrTaggedValue> taggedValues) {
        this.id = id;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.parentId = parentId;
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrPackage)) return false;
        IrPackage that = (IrPackage) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(qualifiedName, that.qualifiedName) &&
                Objects.equals(parentId, that.parentId) &&
                Objects.equals(taggedValues, that.taggedValues);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, qualifiedName, parentId, taggedValues);
    }
}

package info.isaksson.erland.javatoxmi.ir;

import java.util.List;
import java.util.Objects;

/**
 * Root of the cross-language IR.
 */
public final class IrModel {
    public final String schemaVersion;

    /** Optional packaging. Many extractors can omit packages and just use qualifiedName. */
    public final List<IrPackage> packages;

    public final List<IrClassifier> classifiers;
    public final List<IrRelation> relations;

    public final List<IrTaggedValue> taggedValues;

    public IrModel(
            String schemaVersion,
            List<IrPackage> packages,
            List<IrClassifier> classifiers,
            List<IrRelation> relations,
            List<IrTaggedValue> taggedValues
    ) {
        this.schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        this.packages = packages == null ? List.of() : List.copyOf(packages);
        this.classifiers = classifiers == null ? List.of() : List.copyOf(classifiers);
        this.relations = relations == null ? List.of() : List.copyOf(relations);
        this.taggedValues = taggedValues == null ? List.of() : List.copyOf(taggedValues);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrModel)) return false;
        IrModel that = (IrModel) o;
        return Objects.equals(schemaVersion, that.schemaVersion) &&
                Objects.equals(packages, that.packages) &&
                Objects.equals(classifiers, that.classifiers) &&
                Objects.equals(relations, that.relations) &&
                Objects.equals(taggedValues, that.taggedValues);
    }

    @Override public int hashCode() {
        return Objects.hash(schemaVersion, packages, classifiers, relations, taggedValues);
    }
}

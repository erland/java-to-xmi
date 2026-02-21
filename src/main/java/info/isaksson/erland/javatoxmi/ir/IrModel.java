package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * Root of the cross-language IR.
 */
@JsonPropertyOrder({"schemaVersion","packages","classifiers","relations","taggedValues"})
public final class IrModel {
    public final String schemaVersion;

    /** Optional packaging. Many extractors can omit packages and just use qualifiedName. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final List<IrPackage> packages;

    public final List<IrClassifier> classifiers;
    public final List<IrRelation> relations;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final List<IrTaggedValue> taggedValues;

    @JsonCreator
    public IrModel(
            
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("packages") List<IrPackage> packages,
            @JsonProperty("classifiers") List<IrClassifier> classifiers,
            @JsonProperty("relations") List<IrRelation> relations,
            @JsonProperty("taggedValues") List<IrTaggedValue> taggedValues
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

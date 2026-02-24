package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * Defines a stereotype so downstream tools can materialize UML profiles/stereotypes
 * without hard-coded mappings.
 *
 * <p>Intentionally minimal and tool-neutral:
 * - profileName groups stereotypes into profiles
 * - appliesTo lists UML metaclass names (e.g., Class, Interface, Package, Operation, Property, Dependency)
 * - properties defines an optional tagged-value schema
 */
@JsonPropertyOrder({"id","name","qualifiedName","profileName","appliesTo","properties"})
public final class IrStereotypeDefinition {

    /** Stable id referenced from elements. Example: "st:frontend.Component" */
    public final String id;

    public final String name;

    /** Optional fully qualified name (e.g., "Frontend::Component"). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final String qualifiedName;

    /** Optional profile grouping; default chosen by producer if absent. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final String profileName;

    /** UML metaclass names this stereotype may be applied to (e.g., "Class", "Interface"). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final List<String> appliesTo;

    /** Optional tagged value schema. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public final List<IrStereotypePropertyDefinition> properties;

    @JsonCreator
    public IrStereotypeDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("qualifiedName") String qualifiedName,
            @JsonProperty("profileName") String profileName,
            @JsonProperty("appliesTo") List<String> appliesTo,
            @JsonProperty("properties") List<IrStereotypePropertyDefinition> properties
    ) {
        this.id = id;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.profileName = profileName;
        this.appliesTo = appliesTo == null ? List.of() : List.copyOf(appliesTo);
        this.properties = properties == null ? List.of() : List.copyOf(properties);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrStereotypeDefinition)) return false;
        IrStereotypeDefinition that = (IrStereotypeDefinition) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(qualifiedName, that.qualifiedName) &&
                Objects.equals(profileName, that.profileName) &&
                Objects.equals(appliesTo, that.appliesTo) &&
                Objects.equals(properties, that.properties);
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, qualifiedName, profileName, appliesTo, properties);
    }
}

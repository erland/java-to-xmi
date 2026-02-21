package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.uml.AssociationPolicy;
import info.isaksson.erland.javatoxmi.uml.NestedTypesMode;

/** Options for emitting UML/XMI from an IR model. */
public final class EmitterOptions {
    public final String modelName;

    /** When true, attempts to emit stereotypes/profile applications (best-effort). */
    public final boolean includeStereotypes;

    /** When true, emit UML dependencies (from IR relations mapped as dependencies). */
    public final boolean includeDependencies;

    public final AssociationPolicy associationPolicy;
    public final NestedTypesMode nestedTypesMode;

    /** Operation filtering is handled by UmlBuilder; these match the existing CLI flags. */
    public final boolean includeAccessors;
    public final boolean includeConstructors;

    public EmitterOptions(
            String modelName,
            boolean includeStereotypes,
            boolean includeDependencies,
            AssociationPolicy associationPolicy,
            NestedTypesMode nestedTypesMode,
            boolean includeAccessors,
            boolean includeConstructors
    ) {
        this.modelName = (modelName == null || modelName.isBlank()) ? "model" : modelName.trim();
        this.includeStereotypes = includeStereotypes;
        this.includeDependencies = includeDependencies;
        this.associationPolicy = associationPolicy == null ? AssociationPolicy.RESOLVED : associationPolicy;
        this.nestedTypesMode = nestedTypesMode == null ? NestedTypesMode.UML : nestedTypesMode;
        this.includeAccessors = includeAccessors;
        this.includeConstructors = includeConstructors;
    }

    public static EmitterOptions defaults(String modelName) {
        return new EmitterOptions(modelName, false, true, AssociationPolicy.RESOLVED, NestedTypesMode.UML, false, false);
    }

    public EmitterOptions withStereotypes(boolean include) {
        return new EmitterOptions(modelName, include, includeDependencies, associationPolicy, nestedTypesMode, includeAccessors, includeConstructors);
    }

    public EmitterOptions withDependencies(boolean include) {
        return new EmitterOptions(modelName, includeStereotypes, include, associationPolicy, nestedTypesMode, includeAccessors, includeConstructors);
    }

    @Override
    public String toString() {
        return "EmitterOptions{" +
                "modelName='" + modelName + '\'' +
                ", includeStereotypes=" + includeStereotypes +
                ", includeDependencies=" + includeDependencies +
                ", associationPolicy=" + associationPolicy +
                ", nestedTypesMode=" + nestedTypesMode +
                ", includeAccessors=" + includeAccessors +
                ", includeConstructors=" + includeConstructors +
                '}';
    }
}

package info.isaksson.erland.javatoxmi.core;

import info.isaksson.erland.javatoxmi.uml.AssociationPolicy;
import info.isaksson.erland.javatoxmi.uml.NestedTypesMode;

/**
 * Core (server-friendly) options for java-to-xmi conversion.
 *
 * <p>This intentionally mirrors the CLI flags but in a structured form.</p>
 */
public final class JavaToXmiOptions {
    public String modelName = "model";

    public boolean includeStereotypes = true;
    public boolean includeDependencies = false;
    public AssociationPolicy associationPolicy = AssociationPolicy.RESOLVED;
    public NestedTypesMode nestedTypesMode = NestedTypesMode.UML;
    public boolean includeAccessors = false;
    public boolean includeConstructors = false;

    /** Source scanning controls (Java mode). */
    public boolean includeTests = false;

    /**
     * If true, callers may treat unresolved types as an error condition.
     * (Core does not throw by default; this is for upstream policy.)
     */
    public boolean failOnUnresolved = false;
}

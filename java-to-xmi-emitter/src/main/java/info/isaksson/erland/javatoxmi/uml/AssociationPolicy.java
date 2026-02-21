package info.isaksson.erland.javatoxmi.uml;

/**
 * Policy controlling whether fields should be emitted as UML Associations (relationship lines)
 * or kept as attributes-only.
 */
public enum AssociationPolicy {
    /** Never create Associations from fields (attributes-only). */
    NONE("none"),
    /** Only create Associations when JPA relationship annotations are present. */
    JPA_ONLY("jpa"),
    /** Create Associations when the field type resolves to an in-model classifier (current default). */
    RESOLVED("resolved"),
    /** JPA overrides + value-type blacklist + resolved fallback. */
    SMART("smart");

    public final String cliValue;

    AssociationPolicy(String cliValue) {
        this.cliValue = cliValue;
    }

    public static AssociationPolicy parseCli(String v) {
        if (v == null) return RESOLVED;
        String s = v.trim().toLowerCase();
        for (AssociationPolicy p : values()) {
            if (p.cliValue.equals(s)) return p;
        }
        throw new IllegalArgumentException("Invalid value for --associations: " + v + " (expected one of: none|jpa|resolved|smart)");
    }
}

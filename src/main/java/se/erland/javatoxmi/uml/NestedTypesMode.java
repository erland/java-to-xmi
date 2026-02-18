package se.erland.javatoxmi.uml;

/**
 * Controls how nested Java member types are exposed in the generated UML model.
 *
 * <p>In UML, nested member types are best represented as nested classifiers owned by the
 * enclosing classifier. Some importers/tools, however, make it easier to discover nested
 * types when they are also imported into the owning Java package. This enum provides a
 * small set of pragmatic modes to balance interoperability.</p>
 */
public enum NestedTypesMode {
    /**
     * Represent nested Java types as UML nested classifiers owned by the enclosing classifier.
     */
    UML,

    /**
     * Represent nested Java types as UML nested classifiers, and also add an ElementImport
     * for each nested classifier into the owning Java package (consumer-facing convenience).
     */
    UML_IMPORT,

    /**
     * Backwards-compatibility mode: flatten nested Java types into the Java package.
     */
    FLATTEN;

    public static NestedTypesMode parseCli(String v) {
        if (v == null) return UML;
        String s = v.trim().toLowerCase();
        switch (s) {
            case "uml":
                return UML;
            case "uml+import":
            case "uml_import":
            case "uml-import":
                return UML_IMPORT;
            case "flatten":
                return FLATTEN;
            default:
                throw new IllegalArgumentException("Invalid --nested-types mode: " + v + " (expected: uml | uml+import | flatten)");
        }
    }
}

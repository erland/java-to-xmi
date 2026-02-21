package info.isaksson.erland.javatoxmi.ir;

/**
 * Minimal but extensible type reference kinds.
 */
public enum IrTypeRefKind {
    NAMED,
    PRIMITIVE,
    GENERIC,
    ARRAY,
    UNION,
    INTERSECTION,
    UNKNOWN
}

package info.isaksson.erland.javatoxmi.ir;

/**
 * Cross-language classifier kinds. Keep this minimal; extend with taggedValues/stereotypes when needed.
 */
public enum IrClassifierKind {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    TYPE_ALIAS,
    FUNCTION,
    COMPONENT,
    SERVICE,
    MODULE
}

package info.isaksson.erland.javatoxmi.ir;

/**
 * Cross-language relations. Some are structural UML, others are framework graphs (React/Angular).
 */
public enum IrRelationKind {
    GENERALIZATION,
    REALIZATION,
    ASSOCIATION,
    DEPENDENCY,
    COMPOSITION,
    AGGREGATION,

    // Framework / UI graphs
    RENDER,
    DI,
    TEMPLATE_USES,
    ROUTE_TO
}

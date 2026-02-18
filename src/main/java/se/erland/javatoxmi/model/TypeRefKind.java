package se.erland.javatoxmi.model;

/**
 * A lightweight, best-effort structural representation of a Java type.
 *
 * <p>This is intentionally independent of any symbol solver. It can be populated
 * directly from parser AST nodes and refined later if/when better resolution is
 * available.</p>
 */
public enum TypeRefKind {
    /** A non-generic, non-array type like {@code String} or {@code com.acme.Foo}. */
    SIMPLE,
    /** An array type like {@code Foo[]} or {@code int[][]}. */
    ARRAY,
    /** A parameterized type like {@code List<Foo>} or {@code Map<K,V>}. */
    PARAM,
    /** A wildcard type like {@code ?}, {@code ? extends Foo}, {@code ? super Bar}. */
    WILDCARD,
    /** A type variable like {@code T}. */
    TYPEVAR
}

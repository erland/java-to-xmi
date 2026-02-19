package se.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JType {
    public final String packageName;
    public final String name;
    public final String qualifiedName;

    /**
     * Qualified name of the owning/enclosing type if this is a nested member type.
     *
     * <p>Example: package "p"; class Outer { class Inner {} }
     * -> Outer.qualifiedName = "p.Outer", Inner.qualifiedName = "p.Outer.Inner",
     * and Inner.outerQualifiedName = "p.Outer".</p>
     */
    public final String outerQualifiedName;

    /** True if this type is declared inside another type. */
    public final boolean isNested;

    /**
     * True if this is a nested type that is effectively static in Java.
     *
     * <p>Includes {@code static class}, and also nested {@code interface}, {@code enum}
     * and {@code @interface} which are implicitly static in Java.</p>
     */
    public final boolean isStaticNested;

    /** Java binary name (uses {@code $} between nesting levels). */
    public final String binaryName;

    public final JTypeKind kind;
    public final JVisibility visibility;
    public final boolean isAbstract;
    public final boolean isStatic;
    public final boolean isFinal;

    /** Qualified name if resolvable within project, otherwise original string. */
    public final String extendsType;

    /** Qualified names if resolvable within project, otherwise original strings. */
    public final List<String> implementsTypes;

    /** Type-level annotation usages (e.g. {@code @Entity}, {@code @Table(name="x")}). */
    public final List<JAnnotationUse> annotations;

    /** Type-level JavaDoc (best-effort), normalized for stable export. */
    public final String doc;

    public final List<JField> fields;
    public final List<JMethod> methods;

    /**
     * Conservative dependencies derived from method/constructor bodies.
     *
     * <p>These are stored as qualified type names (when resolvable) and are intended to be
     * emitted as UML Dependencies when enabled via CLI flag.</p>
     */
    public final List<String> methodBodyTypeDependencies;

    /** Enum literal names in declaration order. Only applicable when {@link #kind} is {@link JTypeKind#ENUM}. */
    public final List<String> enumLiterals;

    public JType(String packageName,
                 String name,
                 String qualifiedName,
                 String outerQualifiedName,
                 JTypeKind kind,
                 JVisibility visibility,
                 boolean isAbstract,
                 boolean isStatic,
                 boolean isFinal,
                 String extendsType,
                 List<String> implementsTypes,
                 List<JAnnotationUse> annotations,
                 List<JField> fields,
                 List<JMethod> methods,
                 List<String> enumLiterals) {
        this(packageName, name, qualifiedName, outerQualifiedName, kind, visibility, isAbstract, isStatic, isFinal,
                extendsType, implementsTypes, annotations, null, fields, methods, enumLiterals, null);
    }

    public JType(String packageName,
                 String name,
                 String qualifiedName,
                 String outerQualifiedName,
                 JTypeKind kind,
                 JVisibility visibility,
                 boolean isAbstract,
                 boolean isStatic,
                 boolean isFinal,
                 String extendsType,
                 List<String> implementsTypes,
                 List<JAnnotationUse> annotations,
                 String doc,
                 List<JField> fields,
                 List<JMethod> methods,
                 List<String> enumLiterals) {
        this(packageName, name, qualifiedName, outerQualifiedName, kind, visibility, isAbstract, isStatic, isFinal,
                extendsType, implementsTypes, annotations, doc, fields, methods, enumLiterals, null);
    }

    public JType(String packageName,
                 String name,
                 String qualifiedName,
                 String outerQualifiedName,
                 JTypeKind kind,
                 JVisibility visibility,
                 boolean isAbstract,
                 boolean isStatic,
                 boolean isFinal,
                 String extendsType,
                 List<String> implementsTypes,
                 List<JAnnotationUse> annotations,
                 String doc,
                 List<JField> fields,
                 List<JMethod> methods,
                 List<String> enumLiterals,
                 List<String> methodBodyTypeDependencies) {
        this.packageName = Objects.requireNonNullElse(packageName, "");
        this.name = Objects.requireNonNullElse(name, "");
        this.qualifiedName = Objects.requireNonNullElse(qualifiedName, this.name);
        this.outerQualifiedName = (outerQualifiedName == null || outerQualifiedName.isBlank()) ? null : outerQualifiedName;
        this.isNested = this.outerQualifiedName != null;
        this.isStaticNested = this.isNested && (isStatic || kind == JTypeKind.INTERFACE || kind == JTypeKind.ENUM || kind == JTypeKind.ANNOTATION);
        this.binaryName = computeBinaryName(this.packageName, this.qualifiedName);

        this.kind = kind == null ? JTypeKind.CLASS : kind;
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isAbstract = isAbstract;
        this.isStatic = isStatic;
        this.isFinal = isFinal;

        this.extendsType = extendsType;
        this.implementsTypes = implementsTypes == null ? new ArrayList<>() : new ArrayList<>(implementsTypes);
        this.annotations = annotations == null ? new ArrayList<>() : new ArrayList<>(annotations);
        this.doc = Objects.requireNonNullElse(doc, "");
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        this.methods = methods == null ? new ArrayList<>() : new ArrayList<>(methods);
        this.enumLiterals = enumLiterals == null ? new ArrayList<>() : new ArrayList<>(enumLiterals);
        this.methodBodyTypeDependencies = methodBodyTypeDependencies == null ? new ArrayList<>() : new ArrayList<>(methodBodyTypeDependencies);
    }

    /**
     * Compute Java binary name from a qualified name that uses dots for nesting.
     *
     * <p>Examples:
     * <ul>
     *   <li>pkg="p", qn="p.Outer.Inner" -> "p.Outer$Inner"</li>
     *   <li>pkg="", qn="Outer.Inner" -> "Outer$Inner"</li>
     * </ul>
     */
    private static String computeBinaryName(String packageName, String qualifiedName) {
        String pkg = packageName == null ? "" : packageName.trim();
        String qn = qualifiedName == null ? "" : qualifiedName.trim();
        if (qn.isEmpty()) return qn;

        // If we know the package, everything after it is the (possibly nested) top-level type name.
        if (!pkg.isEmpty() && qn.startsWith(pkg + ".")) {
            String tail = qn.substring(pkg.length() + 1);
            return pkg + "." + tail.replace('.', '$');
        }

        // Best-effort fallback when packageName is empty or doesn't match.
        // For the common "Outer.Inner" case (no package), convert nesting dots to '$'.
        if (pkg.isEmpty()) {
            return qn.replace('.', '$');
        }
        // Otherwise, keep the qualified name untouched rather than corrupting package separators.
        return qn;
    }
}

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

    public final JTypeKind kind;
    public final JVisibility visibility;
    public final boolean isAbstract;
    public final boolean isStatic;
    public final boolean isFinal;

    /** Qualified name if resolvable within project, otherwise original string. */
    public final String extendsType;

    /** Qualified names if resolvable within project, otherwise original strings. */
    public final List<String> implementsTypes;

    public final List<JField> fields;
    public final List<JMethod> methods;

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
                 List<JField> fields,
                 List<JMethod> methods,
                 List<String> enumLiterals) {
        this.packageName = Objects.requireNonNullElse(packageName, "");
        this.name = Objects.requireNonNullElse(name, "");
        this.qualifiedName = Objects.requireNonNullElse(qualifiedName, this.name);
        this.outerQualifiedName = (outerQualifiedName == null || outerQualifiedName.isBlank()) ? null : outerQualifiedName;

        this.kind = kind == null ? JTypeKind.CLASS : kind;
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isAbstract = isAbstract;
        this.isStatic = isStatic;
        this.isFinal = isFinal;

        this.extendsType = extendsType;
        this.implementsTypes = implementsTypes == null ? new ArrayList<>() : new ArrayList<>(implementsTypes);
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        this.methods = methods == null ? new ArrayList<>() : new ArrayList<>(methods);
        this.enumLiterals = enumLiterals == null ? new ArrayList<>() : new ArrayList<>(enumLiterals);
    }
}

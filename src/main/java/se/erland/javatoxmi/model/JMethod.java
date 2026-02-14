package se.erland.javatoxmi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JMethod {
    public final String name;
    public final String returnType; // constructors may use empty string
    public final JVisibility visibility;
    public final boolean isStatic;
    public final boolean isAbstract;
    public final boolean isConstructor;
    public final List<JParam> params;

    public JMethod(String name,
                   String returnType,
                   JVisibility visibility,
                   boolean isStatic,
                   boolean isAbstract,
                   boolean isConstructor,
                   List<JParam> params) {
        this.name = Objects.requireNonNullElse(name, "");
        this.returnType = Objects.requireNonNullElse(returnType, "");
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isStatic = isStatic;
        this.isAbstract = isAbstract;
        this.isConstructor = isConstructor;
        this.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
    }
}

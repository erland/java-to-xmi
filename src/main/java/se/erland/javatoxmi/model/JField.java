package se.erland.javatoxmi.model;

import java.util.Objects;

public final class JField {
    public final String name;
    public final String type; // may be unresolved
    public final JVisibility visibility;
    public final boolean isStatic;
    public final boolean isFinal;

    public JField(String name, String type, JVisibility visibility, boolean isStatic, boolean isFinal) {
        this.name = Objects.requireNonNullElse(name, "");
        this.type = Objects.requireNonNullElse(type, "java.lang.Object");
        this.visibility = visibility == null ? JVisibility.PACKAGE_PRIVATE : visibility;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }
}

package se.erland.javatoxmi.model;

import java.util.Objects;

public final class JParam {
    public final String name;
    public final String type; // may be unresolved; keep as string

    public JParam(String name, String type) {
        this.name = Objects.requireNonNullElse(name, "");
        this.type = Objects.requireNonNullElse(type, "java.lang.Object");
    }
}

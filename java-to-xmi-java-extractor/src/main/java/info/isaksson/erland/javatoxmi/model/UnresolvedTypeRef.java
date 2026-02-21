package info.isaksson.erland.javatoxmi.model;

import java.util.Objects;

public final class UnresolvedTypeRef {
    public final String referencedType;     // e.g. java.util.List or Foo
    public final String fromQualifiedType;  // e.g. com.example.MyClass
    public final String where;              // e.g. field 'x', method 'm' param 'p'

    public UnresolvedTypeRef(String referencedType, String fromQualifiedType, String where) {
        this.referencedType = Objects.requireNonNullElse(referencedType, "");
        this.fromQualifiedType = Objects.requireNonNullElse(fromQualifiedType, "");
        this.where = Objects.requireNonNullElse(where, "");
    }

    @Override
    public String toString() {
        return referencedType + " (from " + fromQualifiedType + (where.isEmpty() ? "" : ", " + where) + ")";
    }
}

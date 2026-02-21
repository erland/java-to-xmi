package info.isaksson.erland.javatoxmi.ir;

import java.util.Objects;

public final class IrTaggedValue {
    public final String key;
    public final String value;

    public IrTaggedValue(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrTaggedValue)) return false;
        IrTaggedValue that = (IrTaggedValue) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override public String toString() {
        return "IrTaggedValue{" + key + "=" + value + "}";
    }
}

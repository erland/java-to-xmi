package info.isaksson.erland.javatoxmi.ir;

import java.util.Objects;

/**
 * Optional source reference for traceability back to code.
 */
public final class IrSourceRef {
    public final String file;
    public final Integer line;
    public final Integer col;

    public IrSourceRef(String file, Integer line, Integer col) {
        this.file = file;
        this.line = line;
        this.col = col;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrSourceRef)) return false;
        IrSourceRef that = (IrSourceRef) o;
        return Objects.equals(file, that.file) && Objects.equals(line, that.line) && Objects.equals(col, that.col);
    }

    @Override public int hashCode() {
        return Objects.hash(file, line, col);
    }
}

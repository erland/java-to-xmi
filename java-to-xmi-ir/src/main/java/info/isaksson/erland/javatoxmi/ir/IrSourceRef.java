package info.isaksson.erland.javatoxmi.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Optional source reference for traceability back to code.
 */
@JsonPropertyOrder({"file","line","col"})
public final class IrSourceRef {
    public final String file;
    public final Integer line;
    public final Integer col;

    @JsonCreator
    public IrSourceRef(
            @JsonProperty("file") String file,
            @JsonProperty("line") Integer line,
            @JsonProperty("col") Integer col
    ) {
        this.file = file;
        this.line = line;
        this.col = col;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IrSourceRef)) return false;
        IrSourceRef that = (IrSourceRef) o;
        return Objects.equals(file, that.file) &&
                Objects.equals(line, that.line) &&
                Objects.equals(col, that.col);
    }

    @Override public int hashCode() {
        return Objects.hash(file, line, col);
    }
}

package info.isaksson.erland.javatoxmi.core;

import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuildStats;
import org.eclipse.uml2.uml.Model;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Conversion result container for programmatic usage. */
public final class JavaToXmiResult {
    /** UTF-8 encoded XMI document. */
    public final byte[] xmiBytes;

    /** Convenience: decoded XMI. */
    public final String xmiString;

    /** Present for Java-source mode. */
    public final JModel jModel;

    /** Present when the UML object graph was built (Java-source mode and IR mode). */
    public final Model umlModel;

    /** UML build stats (Java-source mode and IR mode). */
    public final UmlBuildStats stats;

    /** Present for IR mode. */
    public final IrModel irModel;

    /** Present for Java-source mode. */
    public final List<java.nio.file.Path> javaFiles;

    public final int unresolvedTypeCount;

    JavaToXmiResult(
            String xmi,
            JModel jModel,
            Model umlModel,
            UmlBuildStats stats,
            IrModel irModel,
            List<java.nio.file.Path> javaFiles,
            int unresolvedTypeCount
    ) {
        this.xmiString = xmi;
        this.xmiBytes = xmi.getBytes(StandardCharsets.UTF_8);
        this.jModel = jModel;
        this.umlModel = umlModel;
        this.stats = stats;
        this.irModel = irModel;
        this.javaFiles = javaFiles;
        this.unresolvedTypeCount = unresolvedTypeCount;
    }
}

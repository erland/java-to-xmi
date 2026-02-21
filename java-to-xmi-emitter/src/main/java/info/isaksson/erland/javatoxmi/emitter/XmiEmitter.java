package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.ir.IrNormalizer;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;
import info.isaksson.erland.javatoxmi.xmi.XmiWriter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Public API: emit UML XMI from a cross-language IR model.
 *
 * <p>This is designed as a reusable library entrypoint so other extractors (e.g. TS/React/Angular)
 * can feed a JSON IR into this emitter.</p>
 */
public final class XmiEmitter {

    public static final class Result {
        public final org.eclipse.uml2.uml.Model umlModel;
        public final info.isaksson.erland.javatoxmi.uml.UmlBuildStats stats;

        Result(org.eclipse.uml2.uml.Model umlModel, info.isaksson.erland.javatoxmi.uml.UmlBuildStats stats) {
            this.umlModel = umlModel;
            this.stats = stats;
        }
    }

    private final IrToJModelAdapter adapter = new IrToJModelAdapter();

    /**
     * Emit XMI to the given output file.
     *
     * @return build result containing UML model + stats (useful for tests or downstream tooling).
     */
    public Result emit(IrModel ir, EmitterOptions options, Path outXmi) throws IOException {
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (outXmi == null) throw new IllegalArgumentException("outXmi must not be null");
        if (options == null) options = EmitterOptions.defaults("model");

        IrModel normalized = IrNormalizer.normalize(ir);

        JModel jModel = adapter.adapt(normalized, options);

        UmlBuilder.Result uml = new UmlBuilder().build(
                jModel,
                options.modelName,
                options.includeStereotypes,
                options.associationPolicy,
                options.nestedTypesMode,
                options.includeDependencies,
                options.includeAccessors,
                options.includeConstructors
        );

        if (options.includeStereotypes) {
            XmiWriter.write(uml.umlModel, jModel, outXmi);
        } else {
            XmiWriter.write(uml.umlModel, outXmi);
        }

        return new Result(uml.umlModel, uml.stats);
    }
}

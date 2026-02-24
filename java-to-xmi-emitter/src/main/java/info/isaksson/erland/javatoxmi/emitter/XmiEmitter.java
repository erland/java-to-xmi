package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.ir.IrNormalizer;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;
import info.isaksson.erland.javatoxmi.uml.IrStereotypeProfileBuilder;
import info.isaksson.erland.javatoxmi.uml.IrStereotypeApplicator;
import info.isaksson.erland.javatoxmi.xmi.XmiWriter;
import info.isaksson.erland.javatoxmi.emitter.EmitterWarnings;
import info.isaksson.erland.javatoxmi.emitter.EmitterWarning;

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
        public final java.util.List<EmitterWarning> warnings;

        Result(org.eclipse.uml2.uml.Model umlModel, info.isaksson.erland.javatoxmi.uml.UmlBuildStats stats) {
            this(umlModel, stats, java.util.List.of());
        }

        Result(org.eclipse.uml2.uml.Model umlModel, info.isaksson.erland.javatoxmi.uml.UmlBuildStats stats, java.util.List<EmitterWarning> warnings) {
            this.umlModel = umlModel;
            this.stats = stats;
            this.warnings = warnings == null ? java.util.List.of() : warnings;
        }
    }

    /** In-memory emission result (XMI string + UML build info). */
    public static final class StringResult {
        public final String xmi;
        public final Result build;

        StringResult(String xmi, Result build) {
            this.xmi = xmi;
            this.build = build;
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

        EmitterWarnings warningsCollector = new EmitterWarnings();

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

        
        if (options.includeStereotypes && hasIrStereotypes(normalized)) {
            if (normalized.stereotypeDefinitions != null && !normalized.stereotypeDefinitions.isEmpty()) {
                new IrStereotypeProfileBuilder().apply(uml.umlModel, normalized.stereotypeDefinitions);
            }
            new IrStereotypeApplicator().apply(uml.umlModel, normalized, warningsCollector);
        }

        if (options.includeStereotypes) {
            XmiWriter.write(uml.umlModel, jModel, outXmi);
        } else {
            XmiWriter.write(uml.umlModel, outXmi);
        }

        return new Result(uml.umlModel, uml.stats, warningsCollector.toDeterministicList());
    }

    /**
     * Emit XMI as an in-memory string.
     *
     * <p>Useful for server-mode usage where callers want to stream the XMI without
     * writing intermediate files.</p>
     */
    public String emitToString(IrModel ir, EmitterOptions options) throws IOException {
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (options == null) options = EmitterOptions.defaults("model");

        IrModel normalized = IrNormalizer.normalize(ir);

        EmitterWarnings warningsCollector = new EmitterWarnings();
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

        if (options.includeStereotypes && normalized.stereotypeDefinitions != null && !normalized.stereotypeDefinitions.isEmpty()) {
            new IrStereotypeProfileBuilder().apply(uml.umlModel, normalized.stereotypeDefinitions);
        }

        if (options.includeStereotypes) {
            return XmiWriter.writeToString(uml.umlModel, jModel);
        }
        return XmiWriter.writeToString(uml.umlModel, null);
    }

    /** Emit XMI as a string and also return the UML model + stats. */
    public StringResult emitToStringWithResult(IrModel ir, EmitterOptions options) throws IOException {
        if (ir == null) throw new IllegalArgumentException("ir must not be null");
        if (options == null) options = EmitterOptions.defaults("model");

        IrModel normalized = IrNormalizer.normalize(ir);

        EmitterWarnings warningsCollector = new EmitterWarnings();
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

        if (options.includeStereotypes && normalized.stereotypeDefinitions != null && !normalized.stereotypeDefinitions.isEmpty()) {
            new IrStereotypeProfileBuilder().apply(uml.umlModel, normalized.stereotypeDefinitions);
        }

        String xmi = options.includeStereotypes
                ? XmiWriter.writeToString(uml.umlModel, jModel)
                : XmiWriter.writeToString(uml.umlModel, null);

        return new StringResult(xmi, new Result(uml.umlModel, uml.stats));
    }

    private static boolean hasIrStereotypes(IrModel ir) {
        if (ir == null) return false;
        try {
            if (ir.stereotypeDefinitions != null && !ir.stereotypeDefinitions.isEmpty()) return true;
        } catch (Exception ignored) {}
        // Check for any refs on classifiers/relations (some IR writers may omit registry but still emit refs)
        if (ir.classifiers != null) {
            for (var c : ir.classifiers) {
                if (c != null && c.stereotypeRefs != null && !c.stereotypeRefs.isEmpty()) return true;
                if (c != null && c.attributes != null) {
                    for (var a : c.attributes) {
                        if (a != null && a.stereotypeRefs != null && !a.stereotypeRefs.isEmpty()) return true;
                    }
                }
                if (c != null && c.operations != null) {
                    for (var o : c.operations) {
                        if (o != null && o.stereotypeRefs != null && !o.stereotypeRefs.isEmpty()) return true;
                    }
                }
            }
        }
        if (ir.relations != null) {
            for (var r : ir.relations) {
                if (r != null && r.stereotypeRefs != null && !r.stereotypeRefs.isEmpty()) return true;
            }
        }
        return false;
    }

}

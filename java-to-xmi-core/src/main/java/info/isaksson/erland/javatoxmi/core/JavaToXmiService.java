package info.isaksson.erland.javatoxmi.core;

import info.isaksson.erland.javatoxmi.emitter.EmitterOptions;
import info.isaksson.erland.javatoxmi.emitter.XmiEmitter;
import info.isaksson.erland.javatoxmi.extract.JavaExtractor;
import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.uml.UmlBuilder;
import info.isaksson.erland.javatoxmi.xmi.XmiWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Core (server-friendly) API for generating UML XMI.
 *
 * <p>CLI and server wrappers should use this class instead of re-implementing the pipeline.</p>
 */
public final class JavaToXmiService {

    /** Generate XMI from a Java source directory. */
    public JavaToXmiResult generateFromSource(Path sourceRoot, List<String> excludeGlobs, JavaToXmiOptions options) throws IOException {
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot must not be null");
        if (options == null) options = new JavaToXmiOptions();

        List<Path> javaFiles = SourceScanner.scan(sourceRoot, excludeGlobs == null ? List.of() : excludeGlobs, options.includeTests);

        JModel jModel = new JavaExtractor().extract(sourceRoot, javaFiles, options.includeDependencies);

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

        String xmi = XmiWriter.writeToString(uml.umlModel, options.includeStereotypes ? jModel : null);

        int unresolved = jModel.unresolvedTypes == null ? 0 : jModel.unresolvedTypes.size();
        return new JavaToXmiResult(xmi, jModel, uml.umlModel, uml.stats, null, javaFiles, unresolved);
    }

    /** Generate XMI from a cross-language IR model. */
    public JavaToXmiResult generateFromIr(IrModel irModel, JavaToXmiOptions options) throws IOException {
        if (irModel == null) throw new IllegalArgumentException("irModel must not be null");
        if (options == null) options = new JavaToXmiOptions();

        XmiEmitter emitter = new XmiEmitter();

        EmitterOptions emitterOptions = new EmitterOptions(
                options.modelName,
                options.includeStereotypes,
                options.includeDependencies,
                options.associationPolicy,
                options.nestedTypesMode,
                options.includeAccessors,
                options.includeConstructors
        );

        XmiEmitter.StringResult res = emitter.emitToStringWithResult(irModel, emitterOptions);
        return new JavaToXmiResult(res.xmi, null, res.build.umlModel, res.build.stats, irModel, null, 0);
    }
}

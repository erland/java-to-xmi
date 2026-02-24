package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.ir.IrRuntime;
import info.isaksson.erland.javatoxmi.model.JJavaModule;
import info.isaksson.erland.javatoxmi.model.JJavaModuleRequire;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Package;

import java.util.*;

/**
 * Emits JPMS module boundaries as UML packages and {@code requires} edges as dependencies.
 *
 * <p>Packages are annotated with {@code java-to-xmi:runtime} (stereotype=JavaModule) and {@code java-to-xmi:tags}
 * containing exports/opens metadata.</p>
 */
public final class UmlJavaModuleEmitter {

    public void emit(UmlBuildContext ctx, List<JJavaModule> modules) {
        if (ctx == null || ctx.model == null) return;
        if (modules == null || modules.isEmpty()) return;

        // Container package for visibility and determinism
        Package root = ctx.model;
        Package container = root.getNestedPackage("JavaModules");
        if (container == null) {
            container = root.createNestedPackage("JavaModules");
        }

        // Create module packages
        Map<String, Package> byName = new LinkedHashMap<>();
        for (JJavaModule jm : modules) {
            if (jm == null || jm.name == null || jm.name.isBlank()) continue;
            Package p = container.getNestedPackage(jm.name);
            if (p == null) p = container.createNestedPackage(jm.name);

            UmlBuilderSupport.annotateRuntimeStereotype(p, IrRuntime.ST_JAVA_MODULE);

            Map<String, String> tags = new LinkedHashMap<>();
            if (!jm.exports.isEmpty()) tags.put(IrRuntime.Tags.EXPORTS, String.join(",", jm.exports));
            if (!jm.opens.isEmpty()) tags.put(IrRuntime.Tags.OPENS, String.join(",", jm.opens));
            tags.put(IrRuntime.Tags.MODULE, jm.name);
            UmlBuilderSupport.annotateTags(p, tags);

            byName.put(jm.name, p);
        }

        // Create requires edges (dependencies)
        for (JJavaModule jm : modules) {
            Package from = byName.get(jm.name);
            if (from == null) continue;

            for (JJavaModuleRequire req : jm.requires) {
                if (req == null || req.moduleName == null || req.moduleName.isBlank()) continue;

                Package to = byName.get(req.moduleName);
                if (to == null) {
                    // Create placeholder for external modules to keep the dependency meaningful but scoped
                    to = container.getNestedPackage(req.moduleName);
                    if (to == null) to = container.createNestedPackage(req.moduleName);
                    UmlBuilderSupport.annotateRuntimeStereotype(to, IrRuntime.ST_JAVA_MODULE);
                    Map<String, String> tags = new LinkedHashMap<>();
                    tags.put(IrRuntime.Tags.MODULE, req.moduleName);
                    tags.put(IrRuntime.Tags.EXTERNAL, "true");
                    UmlBuilderSupport.annotateTags(to, tags);
                    byName.put(req.moduleName, to);
                }

                Dependency dep = from.createDependency(to);
                dep.setName("requires");

                // Encode modifiers as tags on the dependency for determinism/inspectability
                Map<String, String> depTags = new LinkedHashMap<>();
                depTags.put(IrRuntime.Tags.REQUIRES, req.moduleName);
                if (req.isStatic) depTags.put(IrRuntime.Tags.REQUIRES_STATIC, "true");
                if (req.isTransitive) depTags.put(IrRuntime.Tags.REQUIRES_TRANSITIVE, "true");
                UmlBuilderSupport.annotateTags(dep, depTags);

                ctx.stats.dependenciesCreated++;
            }
        }
    }
}

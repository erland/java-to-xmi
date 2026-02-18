package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared mutable state for a single {@link UmlBuilder} build.
 *
 * <p>This keeps the main builder class small while allowing helpers to share deterministic
 * maps and configuration.</p>
 */
final class UmlBuildContext {
    final Model model;
    final UmlBuildStats stats;
    final MultiplicityResolver multiplicityResolver;
    final AssociationPolicy associationPolicy;
    final NestedTypesMode nestedTypesMode;

    // Deterministic maps
    final Map<String, Package> packageByName = new HashMap<>();
    final Map<String, Classifier> classifierByQName = new HashMap<>();

    UmlBuildContext(Model model,
                    UmlBuildStats stats,
                    MultiplicityResolver multiplicityResolver,
                    AssociationPolicy associationPolicy,
                    NestedTypesMode nestedTypesMode) {
        this.model = model;
        this.stats = stats;
        this.multiplicityResolver = multiplicityResolver;
        this.associationPolicy = associationPolicy;
        this.nestedTypesMode = nestedTypesMode == null ? NestedTypesMode.UML : nestedTypesMode;
    }
}

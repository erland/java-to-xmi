package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import se.erland.javatoxmi.model.JType;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

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

    /** Whether to emit dependency relationships (signature + conservative call graph). */
    final boolean includeDependencies;

    /** Whether to include getter/setter operations when a corresponding field exists. */
    final boolean includeAccessors;

    /** Whether to include constructors as operations. */
    final boolean includeConstructors;

    // Deterministic maps
    final Map<String, Package> packageByName = new HashMap<>();
    final Map<String, Classifier> classifierByQName = new HashMap<>();

    /** Java model types by qualified name (used for safe association merging heuristics). */
    final Map<String, JType> typeByQName = new HashMap<>();

    /** Reverse lookup used for deterministic pair keys and suppression of duplicate dependencies. */
    final Map<Classifier, String> qNameByClassifier = new IdentityHashMap<>();

    /** Undirected association pairs, stored as "<min>|<max>" by qualified name. */
    final Set<String> associationPairs = new HashSet<>();

    /** Candidate associations between type pairs, used to safely merge bidirectional JPA relationships. */
    final Map<String, java.util.List<AssocMergeRecord>> associationRecordsByPair = new HashMap<>();

    UmlBuildContext(Model model,
                    UmlBuildStats stats,
                    MultiplicityResolver multiplicityResolver,
                    AssociationPolicy associationPolicy,
                    NestedTypesMode nestedTypesMode,
                    boolean includeDependencies,
                    boolean includeAccessors,
                    boolean includeConstructors) {
        this.model = model;
        this.stats = stats;
        this.multiplicityResolver = multiplicityResolver;
        this.associationPolicy = associationPolicy;
        this.nestedTypesMode = nestedTypesMode == null ? NestedTypesMode.UML : nestedTypesMode;
        this.includeDependencies = includeDependencies;
        this.includeAccessors = includeAccessors;
        this.includeConstructors = includeConstructors;
    }

    String qNameOf(Classifier c) {
        if (c == null) return null;
        return qNameByClassifier.get(c);
    }

    static String undirectedPairKey(String a, String b) {
        if (a == null || b == null) return null;
        return (a.compareTo(b) <= 0) ? (a + "|" + b) : (b + "|" + a);
    }

    boolean hasAssociationBetween(Classifier a, Classifier b) {
        String qa = qNameOf(a);
        String qb = qNameOf(b);
        if (qa == null || qb == null) return false;
        String key = undirectedPairKey(qa, qb);
        return key != null && associationPairs.contains(key);
    }
}

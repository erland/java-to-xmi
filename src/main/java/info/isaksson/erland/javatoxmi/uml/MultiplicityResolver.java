package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.TypeRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Best-effort multiplicity resolver for UML mapping.
 *
 * <p>This class is intentionally a small coordinator. Rule implementations live in
 * package-private modules to keep each concern testable and maintainable:</p>
 * <ul>
 *   <li>{@link JpaMultiplicityRules} (highest precedence baseline)</li>
 *   <li>{@link BaselineMultiplicityRules} (arrays/collections/Optional/primitives)</li>
 *   <li>{@link ValidationMultiplicityRules} (tightening)</li>
 * </ul>
 */
public final class MultiplicityResolver {

    /** Upper bound value representing '*' (unbounded). */
    public static final int STAR = -1;

    public static final class Result {
        public final int lower;
        public final int upper; // STAR for '*'
        public final Map<String, String> tags;

        public Result(int lower, int upper, Map<String, String> tags) {
            this.lower = Math.max(0, lower);
            this.upper = upper < 0 ? STAR : upper;
            this.tags = tags == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tags));
        }
    }

    /** Resolve multiplicity from a type + annotations. */
    public Result resolve(TypeRef typeRef, List<JAnnotationUse> annotations) {
        List<JAnnotationUse> anns = annotations == null ? List.of() : annotations;

        // 1) Baseline from JPA relation annotations if present
        MutableMultiplicityState st = JpaMultiplicityRules.tryResolveJpaBaseline(anns);

        // 2) Otherwise baseline from structure (arrays/collections/Optional/primitive)
        if (st == null) {
            st = BaselineMultiplicityRules.resolveStructuralBaseline(typeRef);
        }

        // 3) Tighten with validation annotations
        ValidationMultiplicityRules.tightenWithValidation(st, anns);

        return new Result(st.lower, st.upper, st.tags);
    }
}

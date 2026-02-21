package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Property;
import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.JField;
import info.isaksson.erland.javatoxmi.model.JType;

/**
 * JPA-specific opposite-end rules (role name + multiplicity) kept separate from
 * {@link UmlAssociationBuilder} to reduce rule-density in the builder.
 */
final class JpaOppositeEndRules {
    private JpaOppositeEndRules() {}

    static void configureOppositeEndMultiplicity(Property endToSource, JField field) {
        if (endToSource == null) return;

        // Containment-style mappings are treated as 1..1 back to owner.
        if (RelationHeuristics.isEmbedded(field) || RelationHeuristics.isEmbeddedId(field) || RelationHeuristics.isElementCollection(field)) {
            endToSource.setLower(1);
            endToSource.setUpper(1);
            return;
        }

        Multiplicity opp = oppositeMultiplicityFromJpa(field);
        endToSource.setLower(opp.lower);
        endToSource.setUpper(opp.upper == MultiplicityResolver.STAR ? -1 : opp.upper);
    }

    /**
     * Derive a reasonable role name for the opposite association end.
     *
     * <p>For JPA relationships, {@code mappedBy} is the best available canonical inverse role name.
     * For non-bidirectional/unmapped associations we keep the opposite end unnamed to avoid
     * surprising diffs and to preserve prior behavior.</p>
     */
    static String deriveOppositeEndName(JField srcField, JType srcType) {
        String mb = mappedByValue(srcField);
        if (mb != null && !mb.isBlank()) return mb;
        return null;
    }

    /**
     * Extract mappedBy value from common JPA relation annotations.
     */
    static String mappedByValue(JField f) {
        if (f == null || f.annotations == null) return null;
        for (JAnnotationUse a : f.annotations) {
            if (a == null) continue;
            String n = a.qualifiedName != null && !a.qualifiedName.isBlank() ? a.qualifiedName : a.simpleName;
            n = AnnotationValueUtil.stripPkg(n);
            if (!"OneToMany".equals(n) && !"ManyToMany".equals(n) && !"OneToOne".equals(n)) continue;
            String mb = a.values == null ? null : a.values.get("mappedBy");
            if (mb == null) continue;
            mb = mb.trim();
            if (mb.startsWith("\"") && mb.endsWith("\"") && mb.length() >= 2) {
                mb = mb.substring(1, mb.length() - 1);
            }
            if (!mb.isBlank()) return mb;
        }
        return null;
    }

    static final class Multiplicity {
        final int lower;
        final int upper; // use MultiplicityResolver.STAR for *

        Multiplicity(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    /**
     * Derive the opposite association-end multiplicity from JPA relationship annotations.
     */
    static Multiplicity oppositeMultiplicityFromJpa(JField f) {
        if (f == null || f.annotations == null || f.annotations.isEmpty()) {
            return new Multiplicity(0, 1);
        }

        boolean manyToOne = false;
        boolean oneToMany = false;
        boolean manyToMany = false;
        boolean oneToOne = false;

        for (JAnnotationUse a : f.annotations) {
            if (a == null) continue;
            String n = a.qualifiedName != null && !a.qualifiedName.isBlank() ? a.qualifiedName : a.simpleName;
            n = AnnotationValueUtil.stripPkg(n);
            if ("ManyToOne".equals(n)) manyToOne = true;
            else if ("OneToMany".equals(n)) oneToMany = true;
            else if ("ManyToMany".equals(n)) manyToMany = true;
            else if ("OneToOne".equals(n)) oneToOne = true;
        }

        // Highest specificity first
        if (manyToOne) {
            return new Multiplicity(0, MultiplicityResolver.STAR);
        }
        if (oneToMany) {
            return new Multiplicity(0, 1);
        }
        if (manyToMany) {
            return new Multiplicity(0, MultiplicityResolver.STAR);
        }
        if (oneToOne) {
            return new Multiplicity(0, 1);
        }

        return new Multiplicity(0, 1);
    }
}

package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Property;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Encapsulates the JPA bidirectional merge logic so {@link UmlAssociationBuilder}
 * can focus on building UML objects.
 */
final class JpaAssociationMerger {
    private final UmlBuildContext ctx;
    private final Function<JField, AssociationTargetResolver.AssociationTarget> targetComputer;
    private final Function<String, Classifier> classifierResolver;

    JpaAssociationMerger(UmlBuildContext ctx,
                         Function<JField, AssociationTargetResolver.AssociationTarget> targetComputer,
                         Function<String, Classifier> classifierResolver) {
        this.ctx = ctx;
        this.targetComputer = targetComputer;
        this.classifierResolver = classifierResolver;
    }

    /**
     * Merge policy:
     * 1) If mappedBy is present on one side, only merge with the exact inverse field name.
     * 2) Else, merge only when both sides have a unique inverse relationship between the same pair.
     */
    boolean tryMergeIntoExistingAssociation(String srcQn,
                                           String tgtQn,
                                           Classifier srcClassifier,
                                           Classifier tgtClassifier,
                                           JType srcType,
                                           JField srcField,
                                           AssociationTargetResolver.AssociationTarget at,
                                           Property srcOwnedEnd) {
        String pairKey = UmlBuildContext.undirectedPairKey(srcQn, tgtQn);
        if (pairKey == null) return false;

        List<AssocMergeRecord> records = ctx.associationRecordsByPair.get(pairKey);
        if (records == null || records.isEmpty()) return false;

        // 1) If we are the expected inverse for a previously created association (mappedBy): merge deterministically.
        for (AssocMergeRecord r : records) {
            if (r == null || r.association == null) continue;
            if (r.expectedInverseFieldName == null || r.expectedInverseFieldName.isBlank()) continue;
            // r created from r.ownerQn field to r.targetQn and expects inverse field on r.targetQn.
            if (srcQn.equals(r.targetQn) && tgtQn.equals(r.ownerQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // record created from opposite side with mappedBy expecting this field name.
                if (associationAlreadyHasOwnerEnd(r.association, srcClassifier)) return false;
                return mergeAssociation(r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
            }
        }

        // 2) If current field itself uses mappedBy, try to merge into an already-created inverse association.
        String mappedBy = JpaOppositeEndRules.mappedByValue(srcField);
        if (mappedBy != null && !mappedBy.isBlank()) {
            // mappedBy refers to a field on target pointing back to source.
            for (AssocMergeRecord r : records) {
                if (r == null || r.association == null) continue;
                if (!tgtQn.equals(r.ownerQn)) continue;
                if (!srcQn.equals(r.targetQn)) continue;
                if (!mappedBy.equals(r.fieldName)) continue;
                if (associationAlreadyHasOwnerEnd(r.association, srcClassifier)) return false;
                // We found an association created from target.<mappedBy> end. Merge into it.
                return mergeAssociation(r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
            }
            // No inverse created yet. We'll create a new association and index it with expected inverse.
            return false;
        }

        // 3) Unique inverse heuristic (no mappedBy): merge only if it is unambiguous both ways.
        if (!isUniqueInverseJpa(srcQn, tgtQn)) {
            return false;
        }

        // Under uniqueness, we can merge into the single existing record that belongs to the other owner.
        List<AssocMergeRecord> others = new ArrayList<>();
        for (AssocMergeRecord r : records) {
            if (r == null || r.association == null) continue;
            if (!r.ownerQn.equals(srcQn)) others.add(r);
        }
        if (others.size() != 1) return false;
        AssocMergeRecord r = others.get(0);
        if (associationAlreadyHasOwnerEnd(r.association, srcClassifier)) return false;
        return mergeAssociation(r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
    }

    private static boolean associationAlreadyHasOwnerEnd(Association assoc, Classifier owner) {
        if (assoc == null || owner == null) return false;
        for (Property p : assoc.getMemberEnds()) {
            if (p == null) continue;
            if (p.getOwner() == owner) return true;
        }
        return false;
    }

    private boolean mergeAssociation(AssocMergeRecord existing,
                                    Classifier srcClassifier,
                                    Classifier tgtClassifier,
                                    JType srcType,
                                    JField srcField,
                                    AssociationTargetResolver.AssociationTarget at,
                                    Property srcOwnedEnd) {
        if (existing == null || existing.association == null) return false;
        Association assoc = existing.association;

        // Ensure our owned end is connected to the existing association.
        srcOwnedEnd.setAssociation(assoc);
        if (!assoc.getMemberEnds().contains(srcOwnedEnd)) assoc.getMemberEnds().add(srcOwnedEnd);

        // Remove the association-owned placeholder end.
        //
        // When we initially create an association from one side (e.g. Customer.orders -> Order),
        // we create a synthetic opposite end owned by the Association and typed by the *owner* of
        // that field (Customer). When we later merge the inverse field (Order.customer -> Customer),
        // we must remove that synthetic end, otherwise the association ends up with 3 member ends.
        List<Property> ownedEnds = new ArrayList<>(assoc.getOwnedEnds());
        for (Property p : ownedEnds) {
            if (p == null) continue;
            if (p.getType() == tgtClassifier && p.getOwner() == assoc) {
                assoc.getMemberEnds().remove(p);
                assoc.getOwnedEnds().remove(p);
            }
        }

        // Navigability: with both ends classifier-owned, we keep navigableOwnedEnds empty.
        try {
            assoc.getNavigableOwnedEnds().clear();
        } catch (Exception ignored) {
        }

        // Update associationPairs (already present, but idempotent)
        String pairKey = UmlBuildContext.undirectedPairKey(srcType.qualifiedName, ctx.qNameOf(tgtClassifier));
        if (pairKey != null) ctx.associationPairs.add(pairKey);

        // Index this end as well (so later heuristics can detect ambiguity rather than accidentally merging).
        if (pairKey != null) {
            AssocMergeRecord rec = new AssocMergeRecord(assoc, srcType.qualifiedName, srcField.name,
                    ctx.qNameOf(tgtClassifier), null, srcOwnedEnd);
            ctx.associationRecordsByPair.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(rec);
        }

        ctx.stats.associationMerges++;
        return true;
    }

    private boolean isUniqueInverseJpa(String srcQn, String tgtQn) {
        if (ctx == null) return false;
        JType srcType = ctx.typeByQName.get(srcQn);
        JType tgtType = ctx.typeByQName.get(tgtQn);
        if (srcType == null || tgtType == null) return false;

        int srcCount = countJpaRelationshipFieldsTo(srcType, tgtQn);
        if (srcCount != 1) return false;
        int tgtCount = countJpaRelationshipFieldsTo(tgtType, srcQn);
        return tgtCount == 1;
    }

    private int countJpaRelationshipFieldsTo(JType owner, String targetQn) {
        if (owner == null || owner.fields == null) return 0;
        int c = 0;
        for (JField f : owner.fields) {
            if (f == null) continue;
            if (!RelationHeuristics.hasJpaRelationship(f)) continue;
            AssociationTargetResolver.AssociationTarget at = targetComputer.apply(f);
            if (at == null) continue;
            Classifier t = classifierResolver.apply(at.targetRef);
            if (t == null) continue;
            String qn = ctx.qNameOf(t);
            if (targetQn.equals(qn)) c++;
        }
        return c;
    }
}

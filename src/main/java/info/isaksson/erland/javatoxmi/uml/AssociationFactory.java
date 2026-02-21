package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import info.isaksson.erland.javatoxmi.model.JField;
import info.isaksson.erland.javatoxmi.model.JType;

import java.util.ArrayList;

/**
 * Responsible for creating new UML associations (and indexing them for later merge/suppression logic).
 */
final class AssociationFactory {

    void createNewAssociation(UmlBuildContext ctx,
                             Classifier classifier,
                             JType ownerType,
                             JField field,
                             AssociationTargetResolver.AssociationTarget at,
                             Property endToTarget,
                             boolean isJpaRel,
                             String srcQn,
                             String tgtQn) {
        if (ctx == null || classifier == null || ownerType == null || field == null || at == null || endToTarget == null) return;
        if (!(classifier instanceof Type)) return;

        Package ownerPkg = ((Type) classifier).getPackage();
        if (ownerPkg == null) ownerPkg = classifier.getModel();

        Association assoc = UMLFactory.eINSTANCE.createAssociation();
        assoc.setName(null);
        ownerPkg.getPackagedElements().add(assoc);

        Property endToSource = createOppositeEnd(assoc, classifier, field, ownerType);
        configureOppositeEndMultiplicity(endToSource, field);

        // Navigability (unidirectional by default; bidirectional is achieved by merging when safe).
        try {
            assoc.getNavigableOwnedEnds().clear();
        } catch (Exception ignored) {
            // some UML2 versions may throw for unmodifiable lists
        }

        if (!assoc.getMemberEnds().contains(endToTarget)) assoc.getMemberEnds().add(endToTarget);
        if (!assoc.getMemberEnds().contains(endToSource)) assoc.getMemberEnds().add(endToSource);

        ctx.stats.associationsCreated++;
        String assocKey = "Association:" + ownerType.qualifiedName + "#" + field.name + "->" + at.targetRef + ":" + multiplicityKey(at);
        UmlBuilderSupport.annotateId(assoc, assocKey);

        // Record the association pair so dependency creation can suppress duplicates.
        String pairKey = UmlBuildContext.undirectedPairKey(srcQn, tgtQn);
        if (pairKey != null) ctx.associationPairs.add(pairKey);

        UmlBuilderSupport.annotateTags(assoc, RelationTagging.relationDecisionTags(field, ctx.associationPolicy, true));
        UmlBuilderSupport.annotateTags(assoc, RelationTagging.aggregationDecisionTags(field));

        // Index for potential later merge.
        if (isJpaRel && pairKey != null) {
            String mappedBy = JpaOppositeEndRules.mappedByValue(field);
            AssocMergeRecord rec = new AssocMergeRecord(assoc, srcQn, field.name, tgtQn, mappedBy, endToTarget);
            ctx.associationRecordsByPair.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(rec);
        }
    }

    private Property createOppositeEnd(Association assoc, Classifier classifier, JField field, JType ownerType) {
        String oppositeName = JpaOppositeEndRules.deriveOppositeEndName(field, ownerType);
        Property endToSource = assoc.createOwnedEnd(oppositeName, (Type) classifier);
        endToSource.setAggregation(AggregationKind.NONE_LITERAL);
        return endToSource;
    }

    private void configureOppositeEndMultiplicity(Property endToSource, JField field) {
        JpaOppositeEndRules.configureOppositeEndMultiplicity(endToSource, field);
    }

    private static String multiplicityKey(AssociationTargetResolver.AssociationTarget at) {
        if (at == null) return "";
        String up = at.upper < 0 ? "*" : Integer.toString(at.upper);
        return "[" + at.lower + ".." + up + "]";
    }
}

package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.StructuredClassifier;
import org.eclipse.uml2.uml.Type;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JType;
import java.util.Map;

/**
 * Adds conservative field-based associations (or attribute-only), including tagging and aggregation/composition.
 */
final class UmlAssociationBuilder {

    private final AssociationTargetResolver targetResolver = new AssociationTargetResolver();
    private final LocalClassifierResolver classifierResolver = new LocalClassifierResolver();
    private final AssociationFactory associationFactory = new AssociationFactory();

    
    void addFieldAssociations(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;

        for (JField f : t.fields) {
            if (f == null) continue;
            addFieldAssociation(ctx, classifier, t, f);
        }
    }

    private void addFieldAssociation(UmlBuildContext ctx, Classifier classifier, JType ownerType, JField field) {
        AssociationTargetResolver.AssociationTarget at = targetResolver.resolve(ctx, field);
        if (at == null) return;

        Classifier target = classifierResolver.resolveLocalClassifier(ctx, at.targetRef);
        boolean resolvedToClassifier = target != null && target != classifier;

        // Decide whether to create an association line or keep attribute-only.
        boolean createAssoc = RelationHeuristics.shouldCreateAssociation(field, ownerType, ctx.associationPolicy, resolvedToClassifier);

        // Always tag the owned field property with relation decision metadata (when present).
        annotateOwnedAttributeDecision(ctx, classifier, field, resolvedToClassifier);

        if (!createAssoc) return;
        if (target == null) return;
        if (!(classifier instanceof StructuredClassifier) || !(target instanceof Type)) return;

        StructuredClassifier sc = (StructuredClassifier) classifier;
        Type targetType = (Type) target;

        Property endToTarget = ensureEndToTarget(sc, ownerType, field, targetType);
        applyFieldEndMetadata(ctx, endToTarget, field, at, targetType);

        boolean isJpaRel = RelationHeuristics.hasJpaRelationship(field);
        String srcQn = ownerType.qualifiedName;
        String tgtQn = ctx.qNameOf(target);

        // Try merge BEFORE creating a new association.
        if (isJpaRel && srcQn != null && tgtQn != null) {
            JpaAssociationMerger merger = new JpaAssociationMerger(
                    ctx,
                    f -> targetResolver.resolve(ctx, f),
                    ref -> classifierResolver.resolveLocalClassifier(ctx, ref)
            );
            if (merger.tryMergeIntoExistingAssociation(srcQn, tgtQn, classifier, target, ownerType, field, at, endToTarget)) {
                return; // merged
            }
        }

        associationFactory.createNewAssociation(ctx, classifier, ownerType, field, at, endToTarget, isJpaRel, srcQn, tgtQn);
    }

    private void annotateOwnedAttributeDecision(UmlBuildContext ctx,
                                               Classifier classifier,
                                               JField field,
                                               boolean resolvedTarget) {
        if (!(classifier instanceof StructuredClassifier)) return;
        StructuredClassifier sc = (StructuredClassifier) classifier;
        Property owned = findOwnedAttribute(sc, field.name);
        if (owned == null) return;

        UmlBuilderSupport.annotateTags(owned, relationDecisionTags(field, ctx.associationPolicy, resolvedTarget));
        UmlBuilderSupport.annotateTags(owned, RelationTagging.aggregationDecisionTags(field));
    }

    private Property ensureEndToTarget(StructuredClassifier sc, JType ownerType, JField field, Type targetType) {
        Property endToTarget = findOwnedAttribute(sc, field.name);
        if (endToTarget == null) {
            endToTarget = sc.createOwnedAttribute(field.name, targetType);
            UmlBuilderSupport.annotateId(endToTarget, "Field:" + ownerType.qualifiedName + "#" + field.name + ":" + field.type);
            UmlBuilderSupport.setVisibility(endToTarget, field.visibility);
        }
        return endToTarget;
    }

    private void applyFieldEndMetadata(UmlBuildContext ctx,
                                      Property endToTarget,
                                      JField field,
                                      AssociationTargetResolver.AssociationTarget at,
                                      Type targetType) {
        if (endToTarget == null || field == null || at == null) return;

        endToTarget.setType(targetType);
        endToTarget.setLower(at.lower);
        endToTarget.setUpper(at.upper == MultiplicityResolver.STAR ? -1 : at.upper);
        endToTarget.setAggregation(RelationHeuristics.aggregationKindFor(field));
        UmlBuilderSupport.setVisibility(endToTarget, field.visibility);

        UmlBuilderSupport.annotateTags(endToTarget, at.tags);
        UmlBuilderSupport.annotateTags(endToTarget, relationDecisionTags(field, ctx.associationPolicy, true));
        UmlBuilderSupport.annotateTags(endToTarget, RelationTagging.aggregationDecisionTags(field));
    }

    private static Map<String, String> relationDecisionTags(JField f, AssociationPolicy policy, boolean resolvedTarget) {
        return RelationTagging.relationDecisionTags(f, policy, resolvedTarget);
    }

    private static Property findOwnedAttribute(StructuredClassifier sc, String name) {
        if (sc == null || name == null) return null;
        for (Property p : sc.getOwnedAttributes()) {
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    /**
     * Legacy helper used by multiple builders.
     */
    static String stripGenerics(String t) {
        return AssociationTargetResolver.stripGenerics(t);
    }

}

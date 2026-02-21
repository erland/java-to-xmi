package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Property;

/**
 * Index record for a candidate association that may later be merged with an inverse JPA relationship.
 *
 * <p>The generator creates associations field-by-field. For common bidirectional JPA mappings,
 * that would otherwise yield two separate associations between the same two classifiers.
 * We keep a lightweight index so we can safely merge only when we have strong evidence
 * (e.g. mappedBy, or a unique inverse relationship on the other type).</p>
 */
final class AssocMergeRecord {
    final Association association;

    /** Qualified name of the classifier that owns the navigable end property. */
    final String ownerQn;

    /** Name of the field/property on {@link #ownerQn} used as the navigable end. */
    final String fieldName;

    /** Qualified name of the target classifier (the type of the field/property). */
    final String targetQn;

    /**
     * If the JPA mapping uses mappedBy on this side, this is the expected inverse field name
     * on {@link #targetQn}. Otherwise null.
     */
    final String expectedInverseFieldName;

    /** Reference to the owned end property (classifier-owned) that participates in the association. */
    final Property ownedEnd;

    AssocMergeRecord(Association association,
                     String ownerQn,
                     String fieldName,
                     String targetQn,
                     String expectedInverseFieldName,
                     Property ownedEnd) {
        this.association = association;
        this.ownerQn = ownerQn;
        this.fieldName = fieldName;
        this.targetQn = targetQn;
        this.expectedInverseFieldName = expectedInverseFieldName;
        this.ownedEnd = ownedEnd;
    }
}

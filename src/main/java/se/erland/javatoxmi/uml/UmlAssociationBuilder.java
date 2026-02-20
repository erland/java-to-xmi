package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.StructuredClassifier;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.TypeRefKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds conservative field-based associations (or attribute-only), including tagging and aggregation/composition.
 */
final class UmlAssociationBuilder {

    void addFieldAssociations(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;

        for (JField f : t.fields) {
            AssociationTarget at = computeAssociationTarget(ctx, f);
            if (at == null) continue;

            // Resolve the association target to an in-model classifier (if possible)
            Classifier target = resolveLocalClassifier(ctx, at.targetRef);
            boolean resolvedToClassifier = target != null && target != classifier;

            // Decide whether to create an association line or keep attribute-only.
            boolean createAssoc = RelationHeuristics.shouldCreateAssociation(f, t, ctx.associationPolicy, resolvedToClassifier);

            // Always tag the owned field property with relation decision metadata (when present).
            if (classifier instanceof StructuredClassifier) {
                StructuredClassifier sc = (StructuredClassifier) classifier;
                Property owned = findOwnedAttribute(sc, f.name);
                if (owned != null) {
                    UmlBuilderSupport.annotateTags(owned, relationDecisionTags(f, ctx.associationPolicy, resolvedToClassifier));
                    UmlBuilderSupport.annotateTags(owned, aggregationDecisionTags(f));
                }
            }

            if (!createAssoc) {
                continue;
            }

            if (target == null) continue;
            if (!(classifier instanceof StructuredClassifier) || !(target instanceof Type)) continue;

            StructuredClassifier sc = (StructuredClassifier) classifier;
            Property endToTarget = findOwnedAttribute(sc, f.name);
            if (endToTarget == null) {
                endToTarget = sc.createOwnedAttribute(f.name, (Type) target);
                UmlBuilderSupport.annotateId(endToTarget, "Field:" + t.qualifiedName + "#" + f.name + ":" + f.type);
                UmlBuilderSupport.setVisibility(endToTarget, f.visibility);
            }

            endToTarget.setType((Type) target);
            endToTarget.setLower(at.lower);
            endToTarget.setUpper(at.upper == MultiplicityResolver.STAR ? -1 : at.upper);
            endToTarget.setAggregation(RelationHeuristics.aggregationKindFor(f));
            UmlBuilderSupport.setVisibility(endToTarget, f.visibility);

            UmlBuilderSupport.annotateTags(endToTarget, at.tags);
            UmlBuilderSupport.annotateTags(endToTarget, relationDecisionTags(f, ctx.associationPolicy, true));
            UmlBuilderSupport.annotateTags(endToTarget, aggregationDecisionTags(f));

            // --- Safe JPA bidirectional merge policy ---
            // Only attempt to merge associations for JPA relationship fields. For pure "resolved" associations
            // (no JPA semantics) merging would be dangerous because multiple distinct roles can exist.
            boolean isJpaRel = RelationHeuristics.hasJpaRelationship(f);
            String srcQn = t.qualifiedName;
            String tgtQn = ctx.qNameOf(target);

            // Try merge BEFORE creating a new association.
            if (isJpaRel && srcQn != null && tgtQn != null) {
                if (tryMergeIntoExistingAssociation(ctx, srcQn, tgtQn, classifier, target, t, f, at, endToTarget)) {
                    // merged; pair already exists and dependency suppression will work.
                    continue;
                }
            }

            Package ownerPkg = ((Type) classifier).getPackage();
            if (ownerPkg == null) ownerPkg = classifier.getModel();

            Association assoc = UMLFactory.eINSTANCE.createAssociation();
            assoc.setName(null);
            ownerPkg.getPackagedElements().add(assoc);

            // Opposite end: name it for readability and for better round-tripping in tools.
            // Prefer mappedBy (when present) since that is the canonical inverse role name in JPA.
            String oppositeName = deriveOppositeEndName(f, t);
            Property endToSource = assoc.createOwnedEnd(oppositeName, (Type) classifier);
            // By default, UML2 creates a conservative 0..1 opposite end.
            //
            // For "value object" containment (Embedded/EmbeddedId/ElementCollection-of-embeddable),
            // the contained instance conceptually has exactly one owner. Represent that as 1..1 on
            // the owner side (the opposite end typed by the owning entity/class).
            //
            // For other JPA relationships we can still improve the opposite multiplicity even for
            // unidirectional mappings, based on the relationship annotation.
            if (RelationHeuristics.isEmbedded(f) || RelationHeuristics.isEmbeddedId(f) || RelationHeuristics.isElementCollection(f)) {
                endToSource.setLower(1);
                endToSource.setUpper(1);
            } else {
                Multiplicity opp = oppositeMultiplicityFromJpa(f);
                endToSource.setLower(opp.lower);
                endToSource.setUpper(opp.upper == MultiplicityResolver.STAR ? -1 : opp.upper);
            }
            endToSource.setAggregation(AggregationKind.NONE_LITERAL);

            // Navigability (unidirectional by default; bidirectional is achieved by merging when safe).
            try {
                assoc.getNavigableOwnedEnds().clear();
            } catch (Exception ignored) {
                // see comment above
            }

            if (!assoc.getMemberEnds().contains(endToTarget)) assoc.getMemberEnds().add(endToTarget);
            if (!assoc.getMemberEnds().contains(endToSource)) assoc.getMemberEnds().add(endToSource);

            ctx.stats.associationsCreated++;
            String assocKey = "Association:" + t.qualifiedName + "#" + f.name + "->" + at.targetRef + ":" + multiplicityKey(at);
            UmlBuilderSupport.annotateId(assoc, assocKey);

            // Record the association pair so dependency creation can suppress duplicates.
            String pairKey = UmlBuildContext.undirectedPairKey(srcQn, tgtQn);
            if (pairKey != null) ctx.associationPairs.add(pairKey);

            UmlBuilderSupport.annotateTags(assoc, relationDecisionTags(f, ctx.associationPolicy, true));
            UmlBuilderSupport.annotateTags(assoc, aggregationDecisionTags(f));

            // Index for potential later merge.
            if (isJpaRel && pairKey != null) {
                String mappedBy = mappedByValue(f);
                AssocMergeRecord rec = new AssocMergeRecord(assoc, srcQn, f.name, tgtQn, mappedBy, endToTarget);
                ctx.associationRecordsByPair.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(rec);
            }
        }
    }

    /**
     * Merge policy:
     * 1) If mappedBy is present on one side, only merge with the exact inverse field name.
     * 2) Else, merge only when both sides have a unique inverse relationship between the same pair.
     */
    private boolean tryMergeIntoExistingAssociation(UmlBuildContext ctx,
                                                   String srcQn,
                                                   String tgtQn,
                                                   Classifier srcClassifier,
                                                   Classifier tgtClassifier,
                                                   JType srcType,
                                                   JField srcField,
                                                   AssociationTarget at,
                                                   Property srcOwnedEnd) {
        String pairKey = UmlBuildContext.undirectedPairKey(srcQn, tgtQn);
        if (pairKey == null) return false;

        List<AssocMergeRecord> records = ctx.associationRecordsByPair.get(pairKey);
        if (records == null || records.isEmpty()) return false;

        // 1) If current field matches a previously indexed mappedBy expectation: merge deterministically.
        for (AssocMergeRecord r : records) {
            if (r == null || r.association == null) continue;
            if (r.expectedInverseFieldName == null || r.expectedInverseFieldName.isBlank()) continue;
            // r.ownerQn --mappedBy--> r.targetQn, expects inverse field on r.targetQn
            if (tgtQn.equals(r.ownerQn) && srcQn.equals(r.targetQn)) {
                // This record was created from the opposite direction; not a mappedBy expectation for us.
                continue;
            }
            if (srcQn.equals(r.targetQn) && tgtQn.equals(r.ownerQn)) {
                // record: tgt owns end, expects inverse on src
                // not our case
                continue;
            }
            // record created from r.ownerQn side, expects inverse field name on r.targetQn.
            if (tgtQn.equals(r.targetQn) && srcQn.equals(r.ownerQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // Would mean mappedBy points to a field name on target equal to our current field name,
                // but we are on source side. Not typical.
                continue;
            }
            if (srcQn.equals(r.targetQn) && tgtQn.equals(r.ownerQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // record expects inverse on src, but we are src. ignore.
                continue;
            }
            if (srcQn.equals(r.targetQn) || tgtQn.equals(r.targetQn)) {
                // handled below
            }
        }

        // More direct: find record where its expected inverse matches this field on this owner.
        for (AssocMergeRecord r : records) {
            if (r == null || r.association == null) continue;
            if (r.expectedInverseFieldName == null || r.expectedInverseFieldName.isBlank()) continue;
            // r is owned by r.ownerQn, targets r.targetQn, expects inverse field on target.
            if (srcQn.equals(r.targetQn) && tgtQn.equals(r.ownerQn)) {
                // record is opposite direction; not expectation for us.
                continue;
            }
            if (tgtQn.equals(r.targetQn) && srcQn.equals(r.ownerQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // mappedBy expected inverse field on target, but current owner is source; ignore.
                continue;
            }
            if (srcQn.equals(r.targetQn) && tgtQn.equals(r.ownerQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                continue;
            }
            if (srcQn.equals(r.targetQn) && r.ownerQn.equals(tgtQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // record created from target side with mappedBy expecting field on source.
                // we are source, so this is the expected inverse. Merge into r.association.
                return mergeAssociation(ctx, r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
            }
            if (tgtQn.equals(r.targetQn) && r.ownerQn.equals(srcQn) && r.expectedInverseFieldName.equals(srcField.name)) {
                // record created from source side with mappedBy expecting field on target.
                // we are source; not expected.
                continue;
            }
        }

        // 2) If current field itself uses mappedBy, try to merge into an already-created inverse association.
        String mappedBy = mappedByValue(srcField);
        if (mappedBy != null && !mappedBy.isBlank()) {
            // mappedBy refers to a field on target pointing back to source.
            for (AssocMergeRecord r : records) {
                if (r == null || r.association == null) continue;
                if (!tgtQn.equals(r.ownerQn)) continue;
                if (!srcQn.equals(r.targetQn)) continue;
                if (!mappedBy.equals(r.fieldName)) continue;
                // We found an association created from target.<mappedBy> end. Merge into it.
                return mergeAssociation(ctx, r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
            }
            // No inverse created yet. We'll create a new association and index it with expected inverse.
            return false;
        }

        // 3) Unique inverse heuristic (no mappedBy): merge only if it is unambiguous both ways.
        if (!isUniqueInverseJpa(ctx, srcQn, tgtQn, srcType)) {
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
        // Avoid merging twice into the same association for the same owner.
        if (associationAlreadyHasOwnerEnd(r.association, srcClassifier)) return false;
        return mergeAssociation(ctx, r, srcClassifier, tgtClassifier, srcType, srcField, at, srcOwnedEnd);
    }

    private static boolean associationAlreadyHasOwnerEnd(Association assoc, Classifier owner) {
        if (assoc == null || owner == null) return false;
        for (Property p : assoc.getMemberEnds()) {
            if (p == null) continue;
            if (p.getOwner() == owner) return true;
        }
        return false;
    }

    private boolean mergeAssociation(UmlBuildContext ctx,
                                    AssocMergeRecord existing,
                                    Classifier srcClassifier,
                                    Classifier tgtClassifier,
                                    JType srcType,
                                    JField srcField,
                                    AssociationTarget at,
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
        //
        // Therefore, remove association-owned ends typed by the *target* classifier of the field
        // being merged (which is the original owner of the synthetic placeholder end).
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
        String pairKey = UmlBuildContext.undirectedPairKey(srcType.qualifiedName, ctx.qNameOf((Classifier) tgtClassifier));
        if (pairKey != null) ctx.associationPairs.add(pairKey);

        // Index this end as well (so later heuristics can detect ambiguity rather than accidentally merging).
        if (pairKey != null) {
            AssocMergeRecord rec = new AssocMergeRecord(assoc, srcType.qualifiedName, srcField.name,
                    ctx.qNameOf((Classifier) tgtClassifier), null, srcOwnedEnd);
            ctx.associationRecordsByPair.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(rec);
        }

        ctx.stats.associationMerges++;
        return true;
    }

    private boolean isUniqueInverseJpa(UmlBuildContext ctx, String srcQn, String tgtQn, JType srcType) {
        if (ctx == null || srcType == null) return false;
        JType tgtType = ctx.typeByQName.get(tgtQn);
        if (tgtType == null) return false;

        int srcCount = countJpaRelationshipFieldsTo(ctx, srcType, tgtQn);
        if (srcCount != 1) return false;
        int tgtCount = countJpaRelationshipFieldsTo(ctx, tgtType, srcQn);
        return tgtCount == 1;
    }

    private int countJpaRelationshipFieldsTo(UmlBuildContext ctx, JType owner, String targetQn) {
        if (owner == null || owner.fields == null) return 0;
        int c = 0;
        for (JField f : owner.fields) {
            if (f == null) continue;
            if (!RelationHeuristics.hasJpaRelationship(f)) continue;
            AssociationTarget at = computeAssociationTarget(ctx, f);
            if (at == null) continue;
            Classifier t = resolveLocalClassifier(ctx, at.targetRef);
            if (t == null) continue;
            String qn = ctx.qNameOf(t);
            if (targetQn.equals(qn)) c++;
        }
        return c;
    }

    private static String mappedByValue(JField f) {
        if (f == null || f.annotations == null) return null;
        for (se.erland.javatoxmi.model.JAnnotationUse a : f.annotations) {
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

    /**
     * Derive a reasonable role name for the opposite association end.
     *
     * <p>For JPA relationships, {@code mappedBy} is the best available canonical inverse role name.
     * For non-bidirectional/unmapped associations we keep the opposite end unnamed to avoid
     * surprising diffs and to preserve prior behavior (some tests and tools expect unnamed
     * association-owned opposite ends for unidirectional owner-side mappings).</p>
     */
    private static String deriveOppositeEndName(JField srcField, JType srcType) {
        String mb = mappedByValue(srcField);
        if (mb != null && !mb.isBlank()) return mb;
        return null;
    }

    private static final class Multiplicity {
        final int lower;
        final int upper; // use MultiplicityResolver.STAR for *

        Multiplicity(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    /**
     * Derive the opposite association-end multiplicity from JPA relationship annotations.
     *
     * This is intentionally conservative and works even when the mapping is unidirectional.
     *
     * Examples:
     * - @ManyToOne  => opposite is 0..*
     * - @OneToMany  => opposite is 0..1
     * - @ManyToMany => opposite is 0..*
     * - @OneToOne   => opposite is 0..1
     */
    private static Multiplicity oppositeMultiplicityFromJpa(JField f) {
        if (f == null || f.annotations == null || f.annotations.isEmpty()) {
            return new Multiplicity(0, 1);
        }

        boolean manyToOne = false;
        boolean oneToMany = false;
        boolean manyToMany = false;
        boolean oneToOne = false;

        for (se.erland.javatoxmi.model.JAnnotationUse a : f.annotations) {
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

    // -------------------- association target selection --------------------

    static final class AssociationTarget {
        final String targetRef;
        final int lower;
        final int upper;
        final Map<String, String> tags;

        AssociationTarget(String targetRef, int lower, int upper, Map<String, String> tags) {
            this.targetRef = targetRef;
            this.lower = lower;
            this.upper = upper;
            this.tags = tags == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tags));
        }
    }

    private AssociationTarget computeAssociationTarget(UmlBuildContext ctx, JField f) {
        if (f == null) return null;

        // 1) Prefer TypeRef-based target selection when available.
        String target = null;
        if (f.typeRef != null) {
            target = pickAssociationTargetFromTypeRef(f.typeRef);
        }

        // 2) Fallback to legacy string heuristics.
        if (target == null || target.isBlank()) {
            if (f.type == null || f.type.isBlank()) return null;
            String raw = f.type.trim();

            // Arrays
            if (raw.endsWith("[]")) {
                String base = raw.substring(0, raw.length() - 2).trim();
                base = stripGenerics(base);
                target = base;
            } else {
                String base = stripGenerics(raw);
                String simple = simpleName(base);

                // Optional<T> => to T
                if (isOptionalLike(simple)) {
                    String arg0 = firstGenericArg(raw);
                    if (arg0 == null) return null;
                    target = stripArraySuffix(stripGenerics(arg0));
                } else if (isMapLike(simple)) {
                    // Map<K,V> => to V (or K if only one arg)
                    String inner = genericInner(raw);
                    if (inner == null) return null;
                    List<String> args = splitTopLevelTypeArgs(inner);
                    if (args.isEmpty()) return null;
                    String chosen = args.size() >= 2 ? args.get(1) : args.get(0);
                    target = stripArraySuffix(stripGenerics(chosen));
                } else if (isCollectionLike(base)) {
                    // Collection-like containers => to element type
                    String arg0 = firstGenericArg(raw);
                    if (arg0 == null) return null;
                    target = stripArraySuffix(stripGenerics(arg0));
                } else {
                    // Default reference
                    target = stripArraySuffix(stripGenerics(raw));
                }
            }
        }

        MultiplicityResolver.Result mr = ctx.multiplicityResolver.resolve(f.typeRef, f.annotations);
        return new AssociationTarget(target, mr.lower, mr.upper, mr.tags);
    }

    private static Map<String, String> relationDecisionTags(JField f, AssociationPolicy policy, boolean resolvedTarget) {
        if (f == null) return Map.of();
        if (policy == null) policy = AssociationPolicy.RESOLVED;

        if (RelationHeuristics.isTransient(f)) {
            return Map.of("relationSource", "transient", "persistent", "false");
        }

        if (RelationHeuristics.isEmbedded(f)) {
            return Map.of("relationSource", "embedded");
        }
        if (RelationHeuristics.isEmbeddedId(f)) {
            return Map.of("relationSource", "embeddedId");
        }
        if (RelationHeuristics.isElementCollection(f)) {
            return Map.of("relationSource", "elementCollection");
        }

        boolean hasJpa = RelationHeuristics.hasJpaRelationship(f);
        if (hasJpa) {
            return Map.of("relationSource", "jpa");
        }

        switch (policy) {
            case NONE:
                return Map.of("relationSource", "none");
            case JPA_ONLY:
                return Map.of("relationSource", "jpaOnly");
            case SMART:
                if (RelationHeuristics.isValueLike(f)) {
                    return Map.of("relationSource", "valueBlacklist");
                }
                if (resolvedTarget) {
                    return Map.of("relationSource", "resolved");
                }
                return Map.of("relationSource", "unresolved");
            case RESOLVED:
            default:
                return Map.of("relationSource", resolvedTarget ? "resolved" : "unresolved");
        }
    }

    private static Map<String, String> aggregationDecisionTags(JField f) {
        if (f == null) return Map.of();
        AggregationKind ak = RelationHeuristics.aggregationKindFor(f);
        String agg = ak == AggregationKind.COMPOSITE_LITERAL ? "composite" : "none";
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("aggregation", agg);

        if (f.annotations != null) {
            for (se.erland.javatoxmi.model.JAnnotationUse a : f.annotations) {
                if (a == null) continue;
                if ("OneToMany".equals(a.simpleName) || "OneToOne".equals(a.simpleName)
                        || "javax.persistence.OneToMany".equals(a.qualifiedName) || "jakarta.persistence.OneToMany".equals(a.qualifiedName)
                        || "javax.persistence.OneToOne".equals(a.qualifiedName) || "jakarta.persistence.OneToOne".equals(a.qualifiedName)) {
                    String v = a.values == null ? null : a.values.get("orphanRemoval");
                    if (v != null) tags.put("jpaOrphanRemoval", v);
                }
            }
        }
        return Map.copyOf(tags);
    }

    private static String pickAssociationTargetFromTypeRef(TypeRef t) {
        if (t == null) return null;

        if (t.kind == TypeRefKind.ARRAY || safe(t.raw).endsWith("[]")) {
            TypeRef elem = firstArg(t);
            return bestTypeKey(elem);
        }

        if (isContainerLike(t)) {
            TypeRef elem = firstArg(t);
            return bestTypeKey(elem);
        }

        if (isMapLike(t)) {
            if (t.args != null && t.args.size() >= 2) {
                return bestTypeKey(t.args.get(1));
            }
        }

        return bestTypeKey(t);
    }

    private static boolean isContainerLike(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        String raw = safe(t.raw);

        if ("Optional".equals(sn)) return true;
        if ("Collection".equals(sn) || "List".equals(sn) || "Set".equals(sn) || "Iterable".equals(sn)) return true;

        if (qn.endsWith("java.util.Optional")) return true;
        if (qn.endsWith("java.util.Collection") || qn.endsWith("java.util.List") || qn.endsWith("java.util.Set") || qn.endsWith("java.lang.Iterable")) return true;

        return raw.startsWith("Optional<") || raw.startsWith("List<") || raw.startsWith("Set<")
                || raw.startsWith("Collection<") || raw.startsWith("Iterable<");
    }

    private static boolean isMapLike(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        String raw = safe(t.raw);
        if ("Map".equals(sn)) return true;
        if (qn.endsWith("java.util.Map")) return true;
        return raw.startsWith("Map<");
    }

    private static TypeRef firstArg(TypeRef t) {
        if (t == null || t.args == null || t.args.isEmpty()) return null;
        return t.args.get(0);
    }

    private static String bestTypeKey(TypeRef t) {
        if (t == null) return null;
        if (!safe(t.qnameHint).isBlank()) return t.qnameHint;
        if (!safe(t.raw).isBlank()) return t.raw;
        if (!safe(t.simpleName).isBlank()) return t.simpleName;
        return null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String multiplicityKey(AssociationTarget at) {
        if (at == null) return "";
        String up = at.upper < 0 ? "*" : Integer.toString(at.upper);
        return "[" + at.lower + ".." + up + "]";
    }

    private static boolean isOptionalLike(String simpleName) {
        return "Optional".equals(simpleName);
    }

    private static boolean isMapLike(String simpleName) {
        return "Map".equals(simpleName);
    }

    private static String simpleName(String qualifiedOrSimple) {
        if (qualifiedOrSimple == null) return "";
        String s = qualifiedOrSimple.trim();
        if (s.endsWith("[]")) s = s.substring(0, s.length() - 2);
        int li = s.lastIndexOf('.');
        return li >= 0 ? s.substring(li + 1) : s;
    }

    private static String stripArraySuffix(String t) {
        if (t == null) return null;
        String s = t.trim();
        if (s.endsWith("[]")) return s.substring(0, s.length() - 2).trim();
        return s;
    }

    private static String genericInner(String javaTypeRef) {
        if (javaTypeRef == null) return null;
        String s = javaTypeRef.trim();
        int lt = s.indexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt < 0 || gt < lt) return null;
        String inner = s.substring(lt + 1, gt).trim();
        return inner.isEmpty() ? null : inner;
    }

    private static boolean isCollectionLike(String baseType) {
        if (baseType == null) return false;
        String b = baseType;
        if (b.contains(".")) b = b.substring(b.lastIndexOf('.') + 1);
        return "List".equals(b) || "Set".equals(b) || "Collection".equals(b) || "Iterable".equals(b);
    }

    private static String firstGenericArg(String javaTypeRef) {
        if (javaTypeRef == null) return null;
        String s = javaTypeRef.trim();
        int lt = s.indexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt < 0 || gt < lt) return null;
        String inner = s.substring(lt + 1, gt).trim();
        if (inner.isEmpty()) return null;
        int depth = 0;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) break;
            buf.append(c);
        }
        String arg = buf.toString().trim();
        return arg.isEmpty() ? null : arg;
    }

    private static List<String> splitTopLevelTypeArgs(String inner) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);

            if (c == ',' && depth == 0) {
                String part = buf.toString().trim();
                if (!part.isEmpty()) out.add(part);
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        String last = buf.toString().trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    static String stripGenerics(String t) {
        if (t == null) return "";
        int idx = t.indexOf('<');
        if (idx >= 0) {
            return t.substring(0, idx).trim();
        }
        return t.trim();
    }

    private static Property findOwnedAttribute(StructuredClassifier sc, String name) {
        if (sc == null || name == null) return null;
        for (Property p : sc.getOwnedAttributes()) {
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    private static Classifier resolveLocalClassifier(UmlBuildContext ctx, String possiblySimpleOrQualified) {
        if (possiblySimpleOrQualified == null || possiblySimpleOrQualified.isBlank()) return null;
        Classifier direct = ctx.classifierByQName.get(possiblySimpleOrQualified);
        if (direct != null) return direct;

        // If it's a simple name, try to find a unique qualified match.
        String name = possiblySimpleOrQualified;
        if (!name.contains(".")) {
            List<String> matches = new ArrayList<>();
            for (String qn : ctx.classifierByQName.keySet()) {
                if (qn.equals(name) || qn.endsWith("." + name)) {
                    matches.add(qn);
                }
            }
            matches.sort(String::compareTo);
            if (!matches.isEmpty()) {
                return ctx.classifierByQName.get(matches.get(0));
            }
        }
        return null;
    }
}

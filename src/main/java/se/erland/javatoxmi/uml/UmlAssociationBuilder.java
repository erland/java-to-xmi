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
            if (f == null) continue;
            addFieldAssociation(ctx, classifier, t, f);
        }
    }

    private void addFieldAssociation(UmlBuildContext ctx, Classifier classifier, JType ownerType, JField field) {
        AssociationTarget at = computeAssociationTarget(ctx, field);
        if (at == null) return;

        Classifier target = resolveLocalClassifier(ctx, at.targetRef);
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
                    f -> computeAssociationTarget(ctx, f),
                    ref -> resolveLocalClassifier(ctx, ref)
            );
            if (merger.tryMergeIntoExistingAssociation(srcQn, tgtQn, classifier, target, ownerType, field, at, endToTarget)) {
                return; // merged
            }
        }

        createNewAssociation(ctx, classifier, ownerType, field, at, endToTarget, isJpaRel, srcQn, tgtQn);
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
                                      AssociationTarget at,
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

    private void createNewAssociation(UmlBuildContext ctx,
                                     Classifier classifier,
                                     JType ownerType,
                                     JField field,
                                     AssociationTarget at,
                                     Property endToTarget,
                                     boolean isJpaRel,
                                     String srcQn,
                                     String tgtQn) {
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

        UmlBuilderSupport.annotateTags(assoc, relationDecisionTags(field, ctx.associationPolicy, true));
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
        return RelationTagging.relationDecisionTags(f, policy, resolvedTarget);
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

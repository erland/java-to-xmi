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

            Package ownerPkg = ((Type) classifier).getPackage();
            if (ownerPkg == null) ownerPkg = classifier.getModel();

            Association assoc = UMLFactory.eINSTANCE.createAssociation();
            assoc.setName(null);
            ownerPkg.getPackagedElements().add(assoc);

            Property endToSource = assoc.createOwnedEnd(null, (Type) classifier);
            // By default, UML2 creates a conservative 0..1 opposite end. For JPA relationships we can
            // improve the opposite multiplicity even for unidirectional mappings.
            Multiplicity opp = oppositeMultiplicityFromJpa(f);
            endToSource.setLower(opp.lower);
            endToSource.setUpper(opp.upper == MultiplicityResolver.STAR ? -1 : opp.upper);
            endToSource.setAggregation(AggregationKind.NONE_LITERAL);

            // Navigability:
            // - The end owned by the source classifier (endToTarget) is navigable from source -> target.
            // - The opposite end owned by the association (endToSource) should be non-navigable by default.
            //   This matches unidirectional JPA mappings where only the source side is modeled.
            //   (Bidirectional navigation is achieved when the other class has its own field/property.
            //   That field becomes a classifier-owned end and is therefore navigable.)
            //
            // Some UML tools treat association-owned ends as navigable unless navigableOwnedEnd is empty.
            // UML2 exposes navigableOwnedEnd as a subset of ownedEnd; we clear it to indicate that
            // association-owned ends are not navigable.
            try {
                assoc.getNavigableOwnedEnds().clear();
            } catch (Exception ignored) {
                // In older UML2 versions navigableOwnedEnd can be derived/immutable.
                // Ownership still expresses intent: classifier-owned ends are navigable;
                // association-owned ends are treated as non-navigable by most tools.
            }

            if (!assoc.getMemberEnds().contains(endToTarget)) assoc.getMemberEnds().add(endToTarget);
            if (!assoc.getMemberEnds().contains(endToSource)) assoc.getMemberEnds().add(endToSource);

            ctx.stats.associationsCreated++;
            String assocKey = "Association:" + t.qualifiedName + "#" + f.name + "->" + at.targetRef + ":" + multiplicityKey(at);
            UmlBuilderSupport.annotateId(assoc, assocKey);

            // Record the association pair so dependency creation can suppress duplicates.
            String tgtQn = ctx.qNameOf(target);
            String pairKey = UmlBuildContext.undirectedPairKey(t.qualifiedName, tgtQn);
            if (pairKey != null) ctx.associationPairs.add(pairKey);

            UmlBuilderSupport.annotateTags(assoc, relationDecisionTags(f, ctx.associationPolicy, true));
            UmlBuilderSupport.annotateTags(assoc, aggregationDecisionTags(f));
        }
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

        if (RelationHeuristics.isEmbedded(f)) {
            return Map.of("relationSource", "embedded");
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

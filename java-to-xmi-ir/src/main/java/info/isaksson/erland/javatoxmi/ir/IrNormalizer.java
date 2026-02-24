package info.isaksson.erland.javatoxmi.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Produces a stable, deterministic ordering of all IR lists so JSON output is reproducible.
 *
 * <p>Normalization rules are intentionally simple and based on stable keys:
 * packages by qualifiedName/id, classifiers by qualifiedName/id, members by name/id,
 * relations by (kind, sourceId, targetId, name, id).</p>
 *
 * <p>IMPORTANT: Parameter order is preserved as provided (do not sort).</p>
 */
public final class IrNormalizer {

    private IrNormalizer() {}

    public static IrModel normalize(IrModel in) {
        if (in == null) return null;

        List<IrPackage> pkgs = normalizePackages(in.packages);
        List<IrClassifier> clzs = normalizeClassifiers(in.classifiers);
        List<IrRelation> rels = normalizeRelations(in.relations);
        List<IrTaggedValue> tags = normalizeTaggedValues(in.taggedValues);

        return new IrModel(in.schemaVersion, pkgs, clzs, rels, tags);
    }

    private static List<IrPackage> normalizePackages(List<IrPackage> in) {
        if (in == null) return List.of();
        List<IrPackage> out = new ArrayList<>(in.size());
        for (IrPackage p : in) {
            if (p == null) continue;
            out.add(new IrPackage(
                    p.id,
                    p.name,
                    p.qualifiedName,
                    p.parentId,
                    normalizeTaggedValues(p.taggedValues)
            ));
        }
        out.sort(Comparator
                .comparing((IrPackage p) -> safe(p.qualifiedName))
                .thenComparing(p -> safe(p.id))
                .thenComparing(p -> safe(p.name)));
        return List.copyOf(out);
    }

    private static List<IrClassifier> normalizeClassifiers(List<IrClassifier> in) {
        if (in == null) return List.of();
        List<IrClassifier> out = new ArrayList<>(in.size());
        for (IrClassifier c : in) {
            if (c == null) continue;
            out.add(new IrClassifier(
                    c.id,
                    c.name,
                    c.qualifiedName,
                    c.packageId,
                    c.kind,
                    c.visibility,
                    normalizeAttributes(c.attributes),
                    normalizeOperations(c.operations),
                    normalizeStereotypes(c.stereotypes),
                    normalizeTaggedValues(c.taggedValues),
                    c.source
            ));
        }
        out.sort(Comparator
                .comparing((IrClassifier c) -> safe(c.qualifiedName))
                .thenComparing(c -> safe(c.id))
                .thenComparing(c -> safe(c.name)));
        return List.copyOf(out);
    }

    private static List<IrAttribute> normalizeAttributes(List<IrAttribute> in) {
        if (in == null) return List.of();
        List<IrAttribute> out = new ArrayList<>(in.size());
        for (IrAttribute a : in) {
            if (a == null) continue;
            out.add(new IrAttribute(
                    a.id,
                    a.name,
                    a.visibility,
                    a.isStatic,
                    a.isFinal,
                    a.type,
                    normalizeStereotypes(a.stereotypes),
                    normalizeTaggedValues(a.taggedValues),
                    a.source
            ));
        }
        out.sort(Comparator
                .comparing((IrAttribute a) -> safe(a.name))
                .thenComparing(a -> safe(a.id)));
        return List.copyOf(out);
    }

    private static List<IrOperation> normalizeOperations(List<IrOperation> in) {
        if (in == null) return List.of();
        List<IrOperation> out = new ArrayList<>(in.size());
        for (IrOperation o : in) {
            if (o == null) continue;
            out.add(new IrOperation(
                    o.id,
                    o.name,
                    o.visibility,
                    o.isStatic,
                    o.isAbstract,
                    o.isConstructor,
                    o.returnType,
                    normalizeParameters(o.parameters),
                    normalizeStereotypes(o.stereotypes),
                    normalizeTaggedValues(o.taggedValues),
                    o.source
            ));
        }
        out.sort(Comparator
                .comparing((IrOperation o) -> safe(o.name))
                .thenComparing(IrNormalizer::signatureKey)
                .thenComparing(o -> safe(o.id)));
        return List.copyOf(out);
    }

    private static String signatureKey(IrOperation o) {
        if (o == null || o.parameters == null || o.parameters.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (IrParameter p : o.parameters) {
            if (p == null) continue;
            sb.append(safe(p.name)).append(":").append(typeKey(p.type)).append(";");
        }
        return sb.toString();
    }

    private static List<IrParameter> normalizeParameters(List<IrParameter> in) {
        if (in == null) return List.of();
        List<IrParameter> out = new ArrayList<>(in.size());
        for (IrParameter p : in) {
            if (p == null) continue;
            out.add(new IrParameter(
                    p.name,
                    p.type,
                    normalizeTaggedValues(p.taggedValues)
            ));
        }
        // Keep parameter order by index (as provided) - do not sort.
        return List.copyOf(out);
    }

    private static List<IrRelation> normalizeRelations(List<IrRelation> in) {
        if (in == null) return List.of();
        List<IrRelation> out = new ArrayList<>(in.size());
        for (IrRelation r : in) {
            if (r == null) continue;
            out.add(new IrRelation(
                    r.id,
                    r.kind,
                    r.sourceId,
                    r.targetId,
                    r.name,
                    normalizeStereotypes(r.stereotypes),
                    normalizeTaggedValues(r.taggedValues),
                    r.source
            ));
        }
        out.sort(Comparator
                .comparing((IrRelation r) -> r.kind == null ? "" : r.kind.name())
                .thenComparing(r -> safe(r.sourceId))
                .thenComparing(r -> safe(r.targetId))
                .thenComparing(r -> safe(r.name))
                .thenComparing(r -> safe(r.id)));
        return List.copyOf(out);
    }

    private static List<IrStereotype> normalizeStereotypes(List<IrStereotype> in) {
        if (in == null) return List.of();
        List<IrStereotype> out = new ArrayList<>(in.size());
        for (IrStereotype s : in) {
            if (s == null) continue;
            out.add(new IrStereotype(s.name, s.qualifiedName));
        }
        out.sort(Comparator
                .comparing((IrStereotype s) -> safe(s.qualifiedName))
                .thenComparing(s -> safe(s.name)));
        return List.copyOf(out);
    }

    private static List<IrTaggedValue> normalizeTaggedValues(List<IrTaggedValue> in) {
        if (in == null) return List.of();
        List<IrTaggedValue> out = new ArrayList<>(in.size());
        for (IrTaggedValue t : in) {
            if (t == null) continue;
            out.add(new IrTaggedValue(t.key, t.value));
        }
        out.sort(Comparator
                .comparingInt((IrTaggedValue t) -> {
                    String k = safe(t.key);
                    if ("framework".equals(k)) return 0;
                    if (k.startsWith("runtime.")) return 1;
                    return 2;
                })
                .thenComparing(t -> safe(t.key))
                .thenComparing(t -> safe(t.value)));
        return List.copyOf(out);
    }

    private static String typeKey(IrTypeRef t) {
        if (t == null) return "";
        String base = safe(t.name);
        if (t.kind != null) base = t.kind.name() + ":" + base;
        if (t.typeArgs == null || t.typeArgs.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append("<");
        for (IrTypeRef a : t.typeArgs) sb.append(typeKey(a)).append(",");
        sb.append(">");
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

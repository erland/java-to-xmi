package se.erland.javatoxmi.uml;

import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.TypeRefKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves what association target a field should point to (and the resulting multiplicity/tags).
 *
 * Kept as a separate unit so association rules can evolve without growing UmlAssociationBuilder.
 */
final class AssociationTargetResolver {

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

    AssociationTarget resolve(UmlBuildContext ctx, JField f) {
        if (ctx == null || f == null) return null;

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
}

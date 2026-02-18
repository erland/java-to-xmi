package se.erland.javatoxmi.uml;

import se.erland.javatoxmi.model.TypeRef;
import se.erland.javatoxmi.model.TypeRefKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Baseline multiplicity inference from structural type information only.
 *
 * <p>No annotations are considered here.</p>
 */
final class BaselineMultiplicityRules {

    private static final Set<String> PRIMITIVES = Set.of(
            "byte", "short", "int", "long", "float", "double", "boolean", "char", "void"
    );

    private static final Set<String> COLLECTION_SIMPLE_NAMES = Set.of(
            "Collection", "List", "Set", "Iterable"
    );

    static MutableMultiplicityState resolveStructuralBaseline(TypeRef typeRef) {
        if (typeRef == null) {
            return new MutableMultiplicityState(0, 1);
        }

        String raw = safe(typeRef.raw);
        String simple = safe(typeRef.simpleName);

        Map<String, String> tags = new LinkedHashMap<>();

        // Primitive: assume required
        if (typeRef.kind == TypeRefKind.SIMPLE && isPrimitiveName(simple, raw)) {
            MutableMultiplicityState st = new MutableMultiplicityState(1, 1);
            st.tags.putAll(tags);
            return st;
        }

        // Arrays => 0..*
        if (typeRef.kind == TypeRefKind.ARRAY || raw.endsWith("[]")) {
            tags.put("isArray", "true");
            TypeRef elem = firstArg(typeRef);
            if (elem != null) {
                tags.put("elementType", bestTypeLabel(elem));
            }
            MutableMultiplicityState st = new MutableMultiplicityState(0, MultiplicityResolver.STAR);
            st.tags.putAll(tags);
            return st;
        }

        // Optional<T> => 0..1
        if (isOptional(typeRef)) {
            tags.put("containerKind", "Optional");
            TypeRef elem = firstArg(typeRef);
            if (elem != null) tags.put("elementType", bestTypeLabel(elem));
            MutableMultiplicityState st = new MutableMultiplicityState(0, 1);
            st.tags.putAll(tags);
            return st;
        }

        // Collections => 0..*
        if (isCollection(typeRef)) {
            tags.put("collectionKind", bestContainerKind(typeRef));
            TypeRef elem = firstArg(typeRef);
            if (elem != null) tags.put("elementType", bestTypeLabel(elem));
            MutableMultiplicityState st = new MutableMultiplicityState(0, MultiplicityResolver.STAR);
            st.tags.putAll(tags);
            return st;
        }

        // Map<K,V> => treat as 0..* container (association semantics); tag key/value types.
        if (isMap(typeRef)) {
            tags.put("collectionKind", "Map");
            if (typeRef.args != null && typeRef.args.size() >= 2) {
                tags.put("mapKeyType", bestTypeLabel(typeRef.args.get(0)));
                tags.put("mapValueType", bestTypeLabel(typeRef.args.get(1)));
            }
            MutableMultiplicityState st = new MutableMultiplicityState(0, MultiplicityResolver.STAR);
            st.tags.putAll(tags);
            return st;
        }

        // Default reference type
        MutableMultiplicityState st = new MutableMultiplicityState(0, 1);
        st.tags.putAll(tags);
        return st;
    }

    private static boolean isPrimitiveName(String simple, String raw) {
        String s = !simple.isBlank() ? simple : raw;
        s = s.trim();
        return PRIMITIVES.contains(s);
    }

    private static boolean isOptional(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if ("Optional".equals(sn)) return true;
        return qn.endsWith("java.util.Optional") || safe(t.raw).startsWith("Optional<");
    }

    private static boolean isCollection(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if (COLLECTION_SIMPLE_NAMES.contains(sn)) return true;
        return qn.endsWith("java.util.Collection")
                || qn.endsWith("java.util.List")
                || qn.endsWith("java.util.Set")
                || qn.endsWith("java.lang.Iterable");
    }

    private static boolean isMap(TypeRef t) {
        if (t == null) return false;
        String sn = safe(t.simpleName);
        String qn = safe(t.qnameHint);
        if ("Map".equals(sn)) return true;
        return qn.endsWith("java.util.Map") || safe(t.raw).startsWith("Map<");
    }

    private static String bestContainerKind(TypeRef t) {
        if (t == null) return "";
        String sn = safe(t.simpleName);
        if (!sn.isBlank()) return sn;
        String raw = safe(t.raw);
        int idx = raw.indexOf('<');
        String base = idx >= 0 ? raw.substring(0, idx) : raw;
        base = base.trim();
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    private static TypeRef firstArg(TypeRef t) {
        if (t == null || t.args == null || t.args.isEmpty()) return null;
        return t.args.get(0);
    }

    private static String bestTypeLabel(TypeRef t) {
        if (t == null) return "";
        if (!safe(t.qnameHint).isBlank()) return t.qnameHint;
        if (!safe(t.raw).isBlank()) return t.raw;
        return t.simpleName;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

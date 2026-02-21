package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.UnresolvedTypeRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort type name resolution within the scanned project types (no symbol solving).
 */
final class TypeResolver {

    private TypeResolver() {}

    static String resolveTypeRef(Type t,
                                ImportContext ctx,
                                Map<String, Map<String, String>> nestedByOuter,
                                List<String> nestedScopeChain,
                                JModel model,
                                String fromQn,
                                String where) {
        // Preserve full textual form, but resolve the outer-most type name where possible.
        String rendered = renderType(t);
        Set<String> baseNames = TypeNameUtil.extractBaseTypeNames(rendered);

        String primary = TypeNameUtil.primaryBaseName(rendered);
        if (primary == null) {
            return rendered;
        }

        String resolvedPrimary = resolveWithNestedScope(primary, ctx, nestedByOuter, nestedScopeChain);
        if (resolvedPrimary == null) {
            if (primary.contains(".")) {
                model.externalTypeRefs.add(new UnresolvedTypeRef(primary, fromQn, where));
                return rendered;
            }
            if (TypeNameUtil.isNonReferenceType(primary)) {
                return rendered;
            }
            String ext = ctx.qualifyExternal(primary);
            if (ext != null) {
                model.externalTypeRefs.add(new UnresolvedTypeRef(ext, fromQn, where));
                return TypeNameUtil.replacePrimaryBaseName(rendered, primary, ext);
            }
            model.unresolvedTypes.add(new UnresolvedTypeRef(primary, fromQn, where));
            return rendered;
        }

        for (String bn : baseNames) {
            if (bn.equals(primary)) continue;
            if (TypeNameUtil.isNonReferenceType(bn)) continue;
            String r = resolveWithNestedScope(bn, ctx, nestedByOuter, nestedScopeChain);
            if (r == null) {
                if (bn.contains(".")) {
                    model.externalTypeRefs.add(new UnresolvedTypeRef(bn, fromQn, where));
                } else {
                    String ext = ctx.qualifyExternal(bn);
                    if (ext != null) {
                        model.externalTypeRefs.add(new UnresolvedTypeRef(ext, fromQn, where));
                    } else {
                        model.unresolvedTypes.add(new UnresolvedTypeRef(bn, fromQn, where));
                    }
                }
            }
        }

        return TypeNameUtil.replacePrimaryBaseName(rendered, primary, resolvedPrimary);
    }

    static String resolveTypeRef(ClassOrInterfaceType t,
                                ImportContext ctx,
                                Map<String, Map<String, String>> nestedByOuter,
                                List<String> nestedScopeChain,
                                JModel model,
                                String fromQn,
                                String where) {
        return resolveTypeRef((Type) t, ctx, nestedByOuter, nestedScopeChain, model, fromQn, where);
    }

    /**
     * Resolve simple names to nested member types visible in the enclosing type scope.
     */
        /**
     * Resolve simple names to nested member types visible in the enclosing type scope.
     */
    static String resolveWithNestedScope(String typeName,
                                         ImportContext ctx,
                                         Map<String, Map<String, String>> nestedByOuter,
                                         List<String> nestedScopeChain) {
        if (typeName == null || typeName.isBlank()) return null;

        // Normalize Java binary nested name separators ($) to source-style dots.
        String tn = typeName.indexOf('$') >= 0 ? typeName.replace('$', '.') : typeName;

        // Resolve chained nested references like "Inner.Deep" or "Outer.Inner.Deep" by
        // resolving the first segment using the current nested scope, and then walking
        // nestedByOuter from that resolved outer type.
        if (tn.contains(".")) {
            String chainResolved = resolveNestedChain(tn, ctx, nestedByOuter, nestedScopeChain);
            if (chainResolved != null) return chainResolved;
        }

        // Qualified already: attempt direct resolve first
        if (tn.contains(".")) {
            String direct = ctx.resolve(tn);
            if (direct != null) return direct;
        }

        // Check nested scopes from innermost to outermost (simple names only)
        if (nestedScopeChain != null && !tn.contains(".")) {
            for (int i = nestedScopeChain.size() - 1; i >= 0; i--) {
                String outerQn = nestedScopeChain.get(i);
                if (outerQn == null) continue;
                Map<String, String> nested = nestedByOuter.get(outerQn);
                if (nested == null) continue;
                String cand = nested.get(tn);
                if (cand != null) return cand;
            }
        }

        // Fallback: let ImportContext handle: exact project type, explicit/wildcard imports, or currentPackage + name.
        return ctx.resolve(tn);
    }

    private static String resolveNestedChain(String dotted,
                                            ImportContext ctx,
                                            Map<String, Map<String, String>> nestedByOuter,
                                            List<String> nestedScopeChain) {
        if (dotted == null) return null;
        String s = dotted.trim();
        if (s.isEmpty() || !s.contains(".")) return null;

        String[] parts = s.split("\\.");
        if (parts.length < 2) return null;

        // Resolve the first segment in the current nested scope (this is the key case for "Inner.Deep").
        String current = resolveWithNestedScope(parts[0], ctx, nestedByOuter, nestedScopeChain);
        if (current == null) return null;

        for (int i = 1; i < parts.length; i++) {
            String seg = parts[i];
            if (seg == null || seg.isBlank()) return null;

            Map<String, String> nested = nestedByOuter.get(current);
            if (nested != null) {
                String next = nested.get(seg);
                if (next != null) {
                    current = next;
                    continue;
                }
            }

            // Fallback: some chains may not be present in nestedByOuter, but still exist as concrete qualified types.
            String cand = current + "." + seg;
            if (ctx.projectQualifiedTypes.contains(cand)) {
                current = cand;
                continue;
            }

            return null;
        }

        return current;
    }

    static String renderType(Type t) {
        if (t == null) return "java.lang.Object";
        if (t instanceof VoidType) return "void";
        return t.toString();
    }
}

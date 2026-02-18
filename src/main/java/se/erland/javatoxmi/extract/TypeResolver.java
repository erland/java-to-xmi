package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.UnresolvedTypeRef;

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
    static String resolveWithNestedScope(String typeName,
                                         ImportContext ctx,
                                         Map<String, Map<String, String>> nestedByOuter,
                                         List<String> nestedScopeChain) {
        if (typeName == null || typeName.isBlank()) return null;

        // Qualified already: attempt direct resolve first
        if (typeName.contains(".")) {
            String direct = ctx.resolve(typeName);
            if (direct != null) return direct;
        }

        // Handle scoped nested reference like Outer.Inner inside current file
        // Try to resolve first segment via imports/package, then append remainder and see if it's a project type.
        int dot = typeName.indexOf('.');
        if (dot > 0) {
            String first = typeName.substring(0, dot);
            String rest = typeName.substring(dot + 1);
            String resolvedFirst = ctx.resolve(first);
            if (resolvedFirst != null && !rest.isBlank()) {
                String cand = resolvedFirst + "." + rest;
                if (ctx.projectQualifiedTypes.contains(cand)) return cand;
            }
        }

        // Check nested scopes from innermost to outermost
        if (nestedScopeChain != null) {
            for (int i = nestedScopeChain.size() - 1; i >= 0; i--) {
                String outerQn = nestedScopeChain.get(i);
                if (outerQn == null) continue;
                Map<String, String> nested = nestedByOuter.get(outerQn);
                if (nested == null) continue;
                String cand = nested.get(typeName);
                if (cand != null) return cand;
            }
        }

        // Fallback: let ImportContext handle: exact project type, or currentPackage + name.
        return ctx.resolve(typeName);
    }

    static String renderType(Type t) {
        if (t == null) return "java.lang.Object";
        if (t instanceof VoidType) return "void";
        return t.toString();
    }
}

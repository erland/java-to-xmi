package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.type.*;
import info.isaksson.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Parse a JavaParser {@link Type} into a best-effort {@link TypeRef} without symbol solving.
 */
final class TypeRefParser {

    private TypeRefParser() {}

    static TypeRef parse(Type t,
                         Set<String> visibleTypeParams,
                         ImportContext ctx,
                         Map<String, Map<String, String>> nestedByOuter,
                         List<String> nestedScopeChain,
                         JModel model,
                         String fromQn,
                         String where) {
        String raw = TypeResolver.renderType(t);
        if (t == null) {
            return TypeRef.simple(raw, "Object", "java.lang.Object");
        }
        if (t instanceof VoidType) {
            return TypeRef.simple("void", "void", "");
        }
        if (t instanceof PrimitiveType) {
            String s = t.toString();
            return TypeRef.simple(s, s, "");
        }
        if (t instanceof VarType) {
            return TypeRef.simple("var", "var", "");
        }
        if (t instanceof ArrayType) {
            int dims = 0;
            Type inner = t;
            while (inner instanceof ArrayType) {
                dims++;
                inner = ((ArrayType) inner).getComponentType();
            }
            TypeRef comp = parse(inner, visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, fromQn, where);
            return TypeRef.array(raw, comp, dims);
        }
        if (t instanceof WildcardType) {
            WildcardType wt = (WildcardType) t;
            if (wt.getExtendedType().isPresent()) {
                TypeRef bound = parse(wt.getExtendedType().get(), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, fromQn, where);
                return TypeRef.wildcard(raw, WildcardBoundKind.EXTENDS, bound);
            }
            if (wt.getSuperType().isPresent()) {
                TypeRef bound = parse(wt.getSuperType().get(), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, fromQn, where);
                return TypeRef.wildcard(raw, WildcardBoundKind.SUPER, bound);
            }
            return TypeRef.wildcard(raw, WildcardBoundKind.UNBOUNDED, null);
        }
        if (t instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType cit = (ClassOrInterfaceType) t;
            String simple = cit.getNameAsString();

            boolean isTypeVar = (visibleTypeParams != null && visibleTypeParams.contains(simple)
                    && cit.getTypeArguments().isEmpty()
                    && cit.getScope().isEmpty());
            if (isTypeVar) {
                return TypeRef.typeVar(raw, simple);
            }

            String nameWithScope = cit.getNameWithScope();
            String resolved = TypeResolver.resolveWithNestedScope(nameWithScope, ctx, nestedByOuter, nestedScopeChain);
            if (resolved == null) {
                recordUnresolved(nameWithScope, ctx, model, fromQn, where);
                String ext = ctx.qualifyExternal(simple);
                resolved = ext == null ? "" : ext;
            }

            if (cit.getTypeArguments().isPresent() && !cit.getTypeArguments().get().isEmpty()) {
                List<TypeRef> args = new ArrayList<>();
                for (Type at : cit.getTypeArguments().get()) {
                    args.add(parse(at, visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, fromQn, where));
                }
                return TypeRef.param(raw, simple, resolved == null ? "" : resolved, args);
            }

            return TypeRef.simple(raw, simple, resolved == null ? "" : resolved);
        }

        return TypeRef.simple(raw, raw, "");
    }

    private static void recordUnresolved(String typeName, ImportContext ctx, JModel model, String fromQn, String where) {
        if (typeName == null || typeName.isBlank()) return;
        if (TypeNameUtil.isNonReferenceType(typeName)) return;
        if (typeName.contains(".")) {
            model.externalTypeRefs.add(new UnresolvedTypeRef(typeName, fromQn, where));
            return;
        }
        String ext = ctx.qualifyExternal(typeName);
        if (ext != null) {
            model.externalTypeRefs.add(new UnresolvedTypeRef(ext, fromQn, where));
        } else {
            model.unresolvedTypes.add(new UnresolvedTypeRef(typeName, fromQn, where));
        }
    }
}

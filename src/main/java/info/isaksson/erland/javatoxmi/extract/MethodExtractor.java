package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import info.isaksson.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Extracts constructors and methods into {@link JMethod} instances.
 *
 * If enabled, this also extracts body-based dependencies using {@link MethodBodyDependencyExtractor}.
 */
final class MethodExtractor {

    private MethodExtractor() {}

    record MethodExtraction(List<JMethod> methods, List<String> sortedBodyDependencies) {}

    static MethodExtraction extract(
            TypeDeclaration<?> td,
            Set<String> enclosingTypeParams,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            JModel model,
            String ownerQn,
            Map<String, String> fieldTypeByName,
            boolean includeDependencies
    ) {
        List<JMethod> methods = new ArrayList<>();
        Set<String> bodyDeps = includeDependencies ? new HashSet<>() : Collections.emptySet();

        for (BodyDeclaration<?> member : TypeExtractionEngine.getMembers(td)) {
            if (member instanceof ConstructorDeclaration) {
                ConstructorDeclaration cd = (ConstructorDeclaration) member;
                methods.add(extractConstructor(cd, enclosingTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn));
                if (includeDependencies) {
                    bodyDeps.addAll(MethodBodyDependencyExtractor.extract(cd, ctx, nestedByOuter, nestedScopeChain, model, ownerQn, fieldTypeByName));
                }
            } else if (member instanceof MethodDeclaration) {
                MethodDeclaration md = (MethodDeclaration) member;
                methods.add(extractMethod(md, enclosingTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn));
                if (includeDependencies) {
                    bodyDeps.addAll(MethodBodyDependencyExtractor.extract(md, ctx, nestedByOuter, nestedScopeChain, model, ownerQn, fieldTypeByName));
                }
            }
        }

        List<String> sortedBodyDeps = includeDependencies
                ? MethodBodyDependencyExtractor.sortedNormalized(bodyDeps)
                : List.of();

        return new MethodExtraction(methods, sortedBodyDeps);
    }

    private static JMethod extractConstructor(
            ConstructorDeclaration cd,
            Set<String> enclosingTypeParams,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            JModel model,
            String ownerQn
    ) {
        JVisibility vis = TypeExtractionEngine.visibilityOf(cd);
        boolean isStatic = false;
        boolean isAbstract = false;
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(cd, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, cd.getTypeParameters());
        List<JParam> params = new ArrayList<>();
        for (Parameter p : cd.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(
                    p.getType(),
                    ctx,
                    nestedByOuter,
                    nestedScopeChain,
                    model,
                    ownerQn,
                    "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'"
            );
            TypeRef pTypeRef = TypeRefParser.parse(
                    paramTypeForVarArgs(p),
                    visibleTypeParams,
                    ctx,
                    nestedByOuter,
                    nestedScopeChain,
                    model,
                    ownerQn,
                    "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'"
            );
            List<JAnnotationUse> pAnns = AnnotationExtractor.extract(p, ctx);
            params.add(new JParam(p.getNameAsString(), pType, pTypeRef, pAnns));
        }
        return new JMethod(cd.getNameAsString(), "", null, vis, isStatic, isAbstract, true, params, annotations);
    }

    private static JMethod extractMethod(
            MethodDeclaration md,
            Set<String> enclosingTypeParams,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            JModel model,
            String ownerQn
    ) {
        JVisibility vis = TypeExtractionEngine.visibilityOf(md);
        boolean isStatic = md.isStatic();
        boolean isAbstract = md.isAbstract();
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(md, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, md.getTypeParameters());

        String ret = TypeResolver.resolveTypeRef(
                md.getType(),
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model,
                ownerQn,
                "method '" + md.getNameAsString() + "' return"
        );
        TypeRef retRef = TypeRefParser.parse(
                md.getType(),
                visibleTypeParams,
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model,
                ownerQn,
                "method '" + md.getNameAsString() + "' return"
        );
        List<JParam> params = new ArrayList<>();
        for (Parameter p : md.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(
                    p.getType(),
                    ctx,
                    nestedByOuter,
                    nestedScopeChain,
                    model,
                    ownerQn,
                    "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'"
            );
            TypeRef pRef = TypeRefParser.parse(
                    paramTypeForVarArgs(p),
                    visibleTypeParams,
                    ctx,
                    nestedByOuter,
                    nestedScopeChain,
                    model,
                    ownerQn,
                    "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'"
            );
            List<JAnnotationUse> pAnns = AnnotationExtractor.extract(p, ctx);
            params.add(new JParam(p.getNameAsString(), pType, pRef, pAnns));
        }
        return new JMethod(md.getNameAsString(), ret, retRef, vis, isStatic, isAbstract, false, params, annotations);
    }

    private static Type paramTypeForVarArgs(Parameter p) {
        if (p == null) return null;
        if (!p.isVarArgs()) return p.getType();
        // Represent varargs as an array for type-structure purposes.
        return new ArrayType(p.getType());
    }

    private static Set<String> mergeTypeParams(Set<String> enclosing, NodeList<TypeParameter> methodParams) {
        Set<String> out = new HashSet<>();
        if (enclosing != null) out.addAll(enclosing);
        if (methodParams != null) {
            for (TypeParameter tp : methodParams) {
                if (tp == null) continue;
                String n = tp.getNameAsString();
                if (n != null && !n.isBlank()) out.add(n);
            }
        }
        return out;
    }
}

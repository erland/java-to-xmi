package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import se.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Extracts {@link JType} instances from parsed compilation units using the pre-built {@link ProjectTypeIndex}.
 */
final class TypeExtractionEngine {

    private TypeExtractionEngine() {}

    static void extractAllTypes(JModel model, List<ParsedUnit> units, ProjectTypeIndex index, boolean includeDependencies) {
        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            ImportContext ctx = ImportContext.from(u.cu, pkg, index.projectTypeQualifiedNames);

            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;
                extractTypeRecursive(model, ctx, index.nestedByOuter, pkg, td, null, null, List.of(), includeDependencies);
            }
        }
    }

    private static void extractTypeRecursive(
            JModel model,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            String pkg,
            TypeDeclaration<?> td,
            String outerQn,
            String outerPathFromTop,
            List<String> enclosingScopeChain,
            boolean includeDependencies
    ) {
        String name = td.getNameAsString();
        String pathFromTop = (outerPathFromTop == null || outerPathFromTop.isBlank())
                ? name
                : outerPathFromTop + "." + name;
        String qn = qualifiedName(pkg, pathFromTop);

        // Within this type, simple names should resolve to nested member types declared in this type,
        // as well as those in any enclosing types.
        List<String> scopeChain = new ArrayList<>(enclosingScopeChain);
        scopeChain.add(qn);

        extractOneType(model, ctx, nestedByOuter, scopeChain, pkg, td, outerQn, outerPathFromTop, includeDependencies);

        // Recurse into nested member types
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof TypeDeclaration)) continue;
            TypeDeclaration<?> child = (TypeDeclaration<?>) member;
            if (!isSupportedType(child)) continue;
            extractTypeRecursive(model, ctx, nestedByOuter, pkg, child, qn, pathFromTop, scopeChain, includeDependencies);
        }
    }

    private static void extractOneType(
            JModel model,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            String pkg,
            TypeDeclaration<?> td,
            String outerQn,
            String outerPathFromTop,
            boolean includeDependencies
    ) {
        JTypeKind kind = kindOf(td);
        JVisibility vis = visibilityOf(td);
        boolean isAbstract = hasModifier(td, Modifier.Keyword.ABSTRACT);
        boolean isStatic = hasModifier(td, Modifier.Keyword.STATIC);
        boolean isFinal = hasModifier(td, Modifier.Keyword.FINAL);

        String name = td.getNameAsString();
        String qn = qualifiedName(pkg, outerPathFromTop == null || outerPathFromTop.isBlank() ? name : outerPathFromTop + "." + name);

        // Type-level annotations
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(td, ctx);

        // Type-level JavaDoc (best-effort)
        String doc = extractTypeDoc(td);

        // Type parameters visible within the type declaration
        final Set<String> typeParams = new HashSet<>();
        if (td instanceof ClassOrInterfaceDeclaration) {
            for (TypeParameter tp : ((ClassOrInterfaceDeclaration) td).getTypeParameters()) {
                if (tp != null && !tp.getNameAsString().isBlank()) typeParams.add(tp.getNameAsString());
            }
        }

        String extendsType = null;
        List<String> implementsTypes = new ArrayList<>();

        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (!cid.getExtendedTypes().isEmpty()) {
                extendsType = TypeResolver.resolveTypeRef(cid.getExtendedTypes().get(0), ctx, nestedByOuter, nestedScopeChain, model, qn, "extends");
            }
            for (ClassOrInterfaceType it : cid.getImplementedTypes()) {
                implementsTypes.add(TypeResolver.resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
            }
        } else if (td instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) td;
            // enums can implement interfaces
            for (ClassOrInterfaceType it : ed.getImplementedTypes()) {
                implementsTypes.add(TypeResolver.resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
            }
        }

        List<JField> fields = new ArrayList<>();
        Map<String, String> fieldTypeByName = new HashMap<>();
        List<JMethod> methods = new ArrayList<>();
        List<String> enumLiterals = new ArrayList<>();
        Set<String> methodBodyDeps = includeDependencies ? new HashSet<>() : Collections.emptySet();

        // Enum literals
        if (td instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) td;
            for (EnumConstantDeclaration ecd : ed.getEntries()) {
                enumLiterals.add(ecd.getNameAsString());
            }
        }

        // Members: first collect fields (for later this.field call resolution)
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof FieldDeclaration)) continue;
            FieldDeclaration fd = (FieldDeclaration) member;
            JVisibility fVis = visibilityOf(fd);
            boolean fStatic = fd.isStatic();
            boolean fFinal = fd.isFinal();
            List<JAnnotationUse> fAnns = AnnotationExtractor.extract(fd, ctx);
            for (VariableDeclarator var : fd.getVariables()) {
                String fType = TypeResolver.resolveTypeRef(var.getType(), ctx, nestedByOuter, nestedScopeChain, model, qn, "field '" + var.getNameAsString() + "'");
                TypeRef fTypeRef = TypeRefParser.parse(var.getType(), typeParams, ctx, nestedByOuter, nestedScopeChain, model, qn, "field '" + var.getNameAsString() + "'");
                fields.add(new JField(var.getNameAsString(), fType, fTypeRef, fVis, fStatic, fFinal, fAnns));
                fieldTypeByName.put(var.getNameAsString(), fType);
            }
        }

        // Then collect constructors + methods (and optionally body-based dependencies)
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (member instanceof ConstructorDeclaration) {
                ConstructorDeclaration cd = (ConstructorDeclaration) member;
                methods.add(extractConstructor(cd, typeParams, ctx, nestedByOuter, nestedScopeChain, model, qn));
                if (includeDependencies) {
                    methodBodyDeps.addAll(MethodBodyDependencyExtractor.extract(cd, ctx, nestedByOuter, nestedScopeChain, model, qn, fieldTypeByName));
                }
            } else if (member instanceof MethodDeclaration) {
                MethodDeclaration md = (MethodDeclaration) member;
                methods.add(extractMethod(md, typeParams, ctx, nestedByOuter, nestedScopeChain, model, qn));
                if (includeDependencies) {
                    methodBodyDeps.addAll(MethodBodyDependencyExtractor.extract(md, ctx, nestedByOuter, nestedScopeChain, model, qn, fieldTypeByName));
                }
            }
        }

        // If JPA relationship annotations are placed on getter methods (property access), propagate them to fields.
        JavaExtractor.propagateJpaRelationshipAnnotationsFromGettersToFields(fields, methods);

        List<String> sortedBodyDeps = includeDependencies
                ? MethodBodyDependencyExtractor.sortedNormalized(methodBodyDeps)
                : List.of();

        model.types.add(new JType(
                pkg,
                name,
                qn,
                outerQn,
                kind,
                vis,
                isAbstract,
                isStatic,
                isFinal,
                extendsType,
                implementsTypes,
                annotations,
                doc,
                fields,
                methods,
                enumLiterals,
                sortedBodyDeps
        ));
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
        JVisibility vis = visibilityOf(cd);
        boolean isStatic = false;
        boolean isAbstract = false;
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(cd, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, cd.getTypeParameters());
        List<JParam> params = new ArrayList<>();
        for (Parameter p : cd.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            TypeRef pTypeRef = TypeRefParser.parse(paramTypeForVarArgs(p), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'");
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
        JVisibility vis = visibilityOf(md);
        boolean isStatic = md.isStatic();
        boolean isAbstract = md.isAbstract();
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(md, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, md.getTypeParameters());
        String ret = TypeResolver.resolveTypeRef(md.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                "method '" + md.getNameAsString() + "' return");
        TypeRef retRef = TypeRefParser.parse(md.getType(), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                "method '" + md.getNameAsString() + "' return");
        List<JParam> params = new ArrayList<>();
        for (Parameter p : md.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            TypeRef pRef = TypeRefParser.parse(paramTypeForVarArgs(p), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'");
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

    private static String extractTypeDoc(TypeDeclaration<?> td) {
        // Prefer raw comment content (keeps tags/HTML "as-is"), but normalize leading '*' markers.
        return td.getJavadocComment()
                .map(jc -> JavaExtractor.normalizeDocContent(jc.getContent()))
                .orElse("");
    }

    private static boolean isSupportedType(TypeDeclaration<?> td) {
        return (td instanceof ClassOrInterfaceDeclaration
                || td instanceof EnumDeclaration
                || td instanceof AnnotationDeclaration);
    }

    private static List<BodyDeclaration<?>> getMembers(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) td).getMembers();
        }
        if (td instanceof EnumDeclaration) {
            return ((EnumDeclaration) td).getMembers();
        }
        if (td instanceof AnnotationDeclaration) {
            return ((AnnotationDeclaration) td).getMembers();
        }
        return Collections.emptyList();
    }

    private static String qualifiedName(String pkg, String name) {
        if (pkg == null || pkg.isBlank()) return name;
        return pkg + "." + name;
    }

    private static JTypeKind kindOf(TypeDeclaration<?> td) {
        if (td instanceof AnnotationDeclaration) return JTypeKind.ANNOTATION;
        if (td instanceof EnumDeclaration) return JTypeKind.ENUM;
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            return cid.isInterface() ? JTypeKind.INTERFACE : JTypeKind.CLASS;
        }
        return JTypeKind.CLASS;
    }

    private static boolean hasModifier(Object node, Modifier.Keyword kw) {
        if (node instanceof NodeWithModifiers) {
            @SuppressWarnings("rawtypes")
            NodeWithModifiers nwm = (NodeWithModifiers) node;
            return nwm.hasModifier(kw);
        }
        return false;
    }

    private static JVisibility visibilityOf(NodeWithModifiers<?> node) {
        if (node.hasModifier(Modifier.Keyword.PUBLIC)) return JVisibility.PUBLIC;
        if (node.hasModifier(Modifier.Keyword.PROTECTED)) return JVisibility.PROTECTED;
        if (node.hasModifier(Modifier.Keyword.PRIVATE)) return JVisibility.PRIVATE;
        return JVisibility.PACKAGE_PRIVATE;
    }
}

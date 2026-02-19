package se.erland.javatoxmi.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import se.erland.javatoxmi.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Step 3: Parse Java source files into a compact internal semantic model.
 *
 * Baseline type resolution:
 * - Resolve references to types that exist within the same scanned source set:
 *   - same package
 *   - explicit imports
 *   - wildcard imports
 * - Everything else is kept as a string and recorded as unresolved.
 */
public final class JavaExtractor {

    /** Max JavaDoc characters stored per type. If exceeded, text is truncated and suffixed with "…(truncated)". */
    static final int MAX_TYPE_DOC_CHARS = 16 * 1024;

    private final JavaParser parser;

    public JavaExtractor() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setCharacterEncoding(StandardCharsets.UTF_8);
        this.parser = new JavaParser(cfg);
    }

    public JModel extract(Path sourceRoot, List<Path> javaFiles) {
        return extract(sourceRoot, javaFiles, false);
    }

    /**
     * Extract Java model.
     *
     * @param includeMethodBodyCallDependencies when true, extracts additional conservative dependencies
     *                                         from method/constructor bodies (approximate call graph).
     */
    public JModel extract(Path sourceRoot, List<Path> javaFiles, boolean includeMethodBodyCallDependencies) {
        JModel model = new JModel(sourceRoot, javaFiles);

        // 1) Parse all compilation units (collect parse errors but continue)
        List<ParsedUnit> units = new ArrayList<>();
        for (Path f : javaFiles) {
            try {
                String code = Files.readString(f, StandardCharsets.UTF_8);
                CompilationUnit cu = parser.parse(code).getResult()
                        .orElseThrow(() -> new ParseProblemException(List.of()));
                units.add(new ParsedUnit(f, cu));
            } catch (ParseProblemException e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": parse error (" + e.getProblems().size() + " problems)");
            } catch (IOException e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": IO error (" + e.getMessage() + ")");
            } catch (Exception e) {
                model.parseErrors.add(rel(sourceRoot, f) + ": error (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
        }

        // 2) Build project type index (qualified name -> stub)
        // Includes top-level types and nested *member* types (recursively).
        Map<String, TypeStub> projectTypes = new HashMap<>();
        List<TypeInfo> allTypeInfos = new ArrayList<>();
        // outerQualifiedName -> (nestedSimpleName -> nestedQualifiedName)
        Map<String, Map<String, String>> nestedByOuter = new HashMap<>();

        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;
                collectTypeInfosRecursive(allTypeInfos, nestedByOuter, pkg, td, null, td.getNameAsString());
            }
        }
        for (TypeInfo ti : allTypeInfos) {
            projectTypes.put(ti.qualifiedName, new TypeStub(ti.qualifiedName, ti.packageName, ti.simpleName));
        }

        // 3) Extract types
        // We re-walk per compilation unit to keep import context correct for the file.
        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            ImportContext ctx = ImportContext.from(u.cu, pkg, projectTypes.keySet());

            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;
                extractTypeRecursive(model, ctx, nestedByOuter, pkg, td, null, null, List.of(), includeMethodBodyCallDependencies);
            }
        }

// Stable ordering for downstream determinism
        model.types.sort(Comparator.comparing(t -> t.qualifiedName));
        return model;
    }

    private static void collectTypeInfosRecursive(List<TypeInfo> out,
                                                    Map<String, Map<String, String>> nestedByOuter,
                                                    String pkg,
                                                    TypeDeclaration<?> td,
                                                    String outerQn,
                                                    String pathFromTop) {
        if (!isSupportedType(td)) return;
        String simpleName = td.getNameAsString();
        String qn = qualifiedName(pkg, pathFromTop);
        out.add(new TypeInfo(pkg, simpleName, qn, outerQn, td));

        // Collect nested member types recursively
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof TypeDeclaration)) continue;
            TypeDeclaration<?> child = (TypeDeclaration<?>) member;
            if (!isSupportedType(child)) continue;
            String childName = child.getNameAsString();
            String childPath = pathFromTop + "." + childName;
            String childQn = qualifiedName(pkg, childPath);

            nestedByOuter
                    .computeIfAbsent(qn, __ -> new HashMap<>())
                    .put(childName, childQn);

            collectTypeInfosRecursive(out, nestedByOuter, pkg, child, qn, childPath);
        }
    }

    private static void extractTypeRecursive(JModel model,
                                             ImportContext ctx,
                                             Map<String, Map<String, String>> nestedByOuter,
                                             String pkg,
                                             TypeDeclaration<?> td,
                                             String outerQn,
                                             String outerPathFromTop,
                                             List<String> enclosingScopeChain,
                                             boolean includeMethodBodyCallDependencies) {
        String name = td.getNameAsString();
        String pathFromTop = (outerPathFromTop == null || outerPathFromTop.isBlank())
                ? name
                : outerPathFromTop + "." + name;
        String qn = qualifiedName(pkg, pathFromTop);

        // Within this type, simple names should resolve to nested member types declared in this type,
        // as well as those in any enclosing types.
        List<String> scopeChain = new ArrayList<>(enclosingScopeChain);
        scopeChain.add(qn);

        extractOneType(model, ctx, nestedByOuter, scopeChain, pkg, td, outerQn, outerPathFromTop, includeMethodBodyCallDependencies);

        // Recurse into nested member types
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof TypeDeclaration)) continue;
            TypeDeclaration<?> child = (TypeDeclaration<?>) member;
            if (!isSupportedType(child)) continue;
            extractTypeRecursive(model, ctx, nestedByOuter, pkg, child, qn, pathFromTop, scopeChain, includeMethodBodyCallDependencies);
        }
    }

    private static void extractOneType(JModel model,
                                       ImportContext ctx,
                                       Map<String, Map<String, String>> nestedByOuter,
                                       List<String> nestedScopeChain,
                                       String pkg,
                                       TypeDeclaration<?> td,
                                       String outerQn) {
        extractOneType(model, ctx, nestedByOuter, nestedScopeChain, pkg, td, outerQn, null);
    }

    private static void extractOneType(JModel model,
                                       ImportContext ctx,
                                       Map<String, Map<String, String>> nestedByOuter,
                                       List<String> nestedScopeChain,
                                       String pkg,
                                       TypeDeclaration<?> td,
                                       String outerQn,
                                       String outerSimpleName) {
        extractOneType(model, ctx, nestedByOuter, nestedScopeChain, pkg, td, outerQn, outerSimpleName, false);
    }

    private static void extractOneType(JModel model,
                                       ImportContext ctx,
                                       Map<String, Map<String, String>> nestedByOuter,
                                       List<String> nestedScopeChain,
                                       String pkg,
                                       TypeDeclaration<?> td,
                                       String outerQn,
                                       String outerSimpleName,
                                       boolean includeMethodBodyCallDependencies) {
        JTypeKind kind = kindOf(td);
        JVisibility vis = visibilityOf(td);
        boolean isAbstract = hasModifier(td, Modifier.Keyword.ABSTRACT);
        boolean isStatic = hasModifier(td, Modifier.Keyword.STATIC);
        boolean isFinal = hasModifier(td, Modifier.Keyword.FINAL);

        String name = td.getNameAsString();
        String qn;
        if (outerSimpleName != null && !outerSimpleName.isBlank()) {
            qn = qualifiedName(pkg, outerSimpleName + "." + name);
        } else {
            qn = qualifiedName(pkg, name);
        }


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
                Set<String> methodBodyDeps = includeMethodBodyCallDependencies ? new HashSet<>() : Collections.emptySet();

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
                        if (includeMethodBodyCallDependencies) {
                            methodBodyDeps.addAll(MethodBodyDependencyExtractor.extract(cd, ctx, nestedByOuter, nestedScopeChain, model, qn, fieldTypeByName));
                        }
                    } else if (member instanceof MethodDeclaration) {
                        MethodDeclaration md = (MethodDeclaration) member;
                        methods.add(extractMethod(md, typeParams, ctx, nestedByOuter, nestedScopeChain, model, qn));
                        if (includeMethodBodyCallDependencies) {
                            methodBodyDeps.addAll(MethodBodyDependencyExtractor.extract(md, ctx, nestedByOuter, nestedScopeChain, model, qn, fieldTypeByName));
                        }
                    }
                }

                List<String> sortedBodyDeps = includeMethodBodyCallDependencies
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

    private static String extractTypeDoc(TypeDeclaration<?> td) {
        // Prefer raw comment content (keeps tags/HTML "as-is"), but normalize leading '*' markers.
        return td.getJavadocComment()
                .map(jc -> normalizeDocContent(jc.getContent()))
                .orElse("");
    }

    static String normalizeDocContent(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = s.split("\n", -1);
        // Normalize per line first (preserve newlines), then trim leading/trailing blank lines.
        String[] normLines = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Remove common JavaDoc leading '*' decoration.
            String trimmedLeft = line.stripLeading();
            if (trimmedLeft.startsWith("*")) {
                trimmedLeft = trimmedLeft.substring(1);
                if (trimmedLeft.startsWith(" ")) trimmedLeft = trimmedLeft.substring(1);
                line = trimmedLeft;
            }
            // Collapse consecutive spaces/tabs within the line, preserve newlines.
            line = line.replace('\t', ' ');
            line = line.replaceAll(" {2,}", " ");
            // Avoid trailing whitespace noise that often appears in JavaDoc blocks.
            line = line.stripTrailing();
            normLines[i] = line;
        }

        int start = 0;
        int end = normLines.length; // exclusive
        while (start < end && normLines[start].isBlank()) start++;
        while (end > start && normLines[end - 1].isBlank()) end--;

        StringBuilder out = new StringBuilder(s.length());
        for (int i = start; i < end; i++) {
            out.append(normLines[i]);
            if (i < end - 1) out.append('\n');
        }

        String normalized = out.toString();
        if (normalized.length() <= MAX_TYPE_DOC_CHARS) return normalized;
        String suffix = "…(truncated)";
        int keep = Math.max(0, MAX_TYPE_DOC_CHARS - suffix.length());
        return normalized.substring(0, keep) + suffix;
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
        // RecordDeclaration etc. could be added later; v1 baseline ignores.
        return Collections.emptyList();
    }

    private static JMethod extractConstructor(ConstructorDeclaration cd,
                                              Set<String> enclosingTypeParams,
                                              ImportContext ctx,
                                              Map<String, Map<String, String>> nestedByOuter,
                                              List<String> nestedScopeChain,
                                              JModel model,
                                              String ownerQn) {
        JVisibility vis = visibilityOf(cd);
        boolean isStatic = false;
        boolean isAbstract = false;
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(cd, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, cd.getTypeParameters());
        List<JParam> params = new ArrayList<>();
        for (Parameter p : cd.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            TypeRef pTypeRef = TypeRefParser.parse(paramTypeForVarArgs(p), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            List<JAnnotationUse> pAnns = AnnotationExtractor.extract(p, ctx);
            params.add(new JParam(p.getNameAsString(), pType, pTypeRef, pAnns));
        }
        return new JMethod(cd.getNameAsString(), "", null, vis, isStatic, isAbstract, true, params, annotations);
    }

    private static JMethod extractMethod(MethodDeclaration md,
                                         Set<String> enclosingTypeParams,
                                         ImportContext ctx,
                                         Map<String, Map<String, String>> nestedByOuter,
                                         List<String> nestedScopeChain,
                                         JModel model,
                                         String ownerQn) {
        JVisibility vis = visibilityOf(md);
        boolean isStatic = md.isStatic();
        boolean isAbstract = md.isAbstract();
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(md, ctx);
        Set<String> visibleTypeParams = mergeTypeParams(enclosingTypeParams, md.getTypeParameters());
        String ret = TypeResolver.resolveTypeRef(md.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "method '" + md.getNameAsString() + "' return");
        TypeRef retRef = TypeRefParser.parse(md.getType(), visibleTypeParams, ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "method '" + md.getNameAsString() + "' return");
        List<JParam> params = new ArrayList<>();
        for (Parameter p : md.getParameters()) {
            String pType = TypeResolver.resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'");
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

    private static String qualifiedName(String pkg, String name) {
        if (pkg == null || pkg.isBlank()) return name;
        return pkg + "." + name;
    }

    private static String rel(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toString().replace('\\', '/');
        }
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
        if (node.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC)) return JVisibility.PUBLIC;
        if (node.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PROTECTED)) return JVisibility.PROTECTED;
        if (node.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PRIVATE)) return JVisibility.PRIVATE;
        return JVisibility.PACKAGE_PRIVATE;
    }

    private static final class ParsedUnit {
        final Path file;
        final CompilationUnit cu;
        ParsedUnit(Path file, CompilationUnit cu) { this.file = file; this.cu = cu; }
    }

    private record TypeStub(String qualifiedName, String pkg, String simpleName) {}

    private record TypeInfo(String packageName, String simpleName, String qualifiedName, String outerQualifiedName, TypeDeclaration<?> declaration) {}


    // ImportContext, TypeNameUtil, annotation parsing and TypeRef parsing have been extracted
    // into dedicated helpers in this package to reduce JavaExtractor complexity.
}

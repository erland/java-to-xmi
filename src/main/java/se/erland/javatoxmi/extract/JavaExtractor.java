package se.erland.javatoxmi.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
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

    private final JavaParser parser;

    public JavaExtractor() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setCharacterEncoding(StandardCharsets.UTF_8);
        this.parser = new JavaParser(cfg);
    }

    public JModel extract(Path sourceRoot, List<Path> javaFiles) {
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
        // Includes top-level types and nested *member* types inside top-level types.
        Map<String, TypeStub> projectTypes = new HashMap<>();
        List<TypeInfo> allTypeInfos = new ArrayList<>();
        // outerQualifiedName -> (nestedSimpleName -> nestedQualifiedName)
        Map<String, Map<String, String>> nestedByOuter = new HashMap<>();
        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;

                String topName = td.getNameAsString();
                String topQn = qualifiedName(pkg, topName);
                TypeInfo top = new TypeInfo(pkg, topName, topQn, null, td);
                allTypeInfos.add(top);

                // Nested member types (one level deep) inside top-level types
                for (BodyDeclaration<?> member : getMembers(td)) {
                    if (!(member instanceof TypeDeclaration)) continue;
                    TypeDeclaration<?> ntd = (TypeDeclaration<?>) member;
                    if (!isSupportedType(ntd)) continue;
                    String innerName = ntd.getNameAsString();
                    String innerQn = qualifiedName(pkg, topName + "." + innerName);
                    allTypeInfos.add(new TypeInfo(pkg, innerName, innerQn, topQn, ntd));

                    nestedByOuter
                            .computeIfAbsent(topQn, __ -> new HashMap<>())
                            .put(innerName, innerQn);
                }
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

                // Extract top-level
                String topQn = qualifiedName(pkg, td.getNameAsString());
                extractOneType(model, ctx, nestedByOuter, List.of(topQn), pkg, td, null);

                // Extract nested member types (one level deep)
                String topName = td.getNameAsString();
                for (BodyDeclaration<?> member : getMembers(td)) {
                    if (!(member instanceof TypeDeclaration)) continue;
                    TypeDeclaration<?> ntd = (TypeDeclaration<?>) member;
                    if (!isSupportedType(ntd)) continue;
                    // Within a nested member type, simple names should resolve to siblings declared in the enclosing type.
                    extractOneType(model, ctx, nestedByOuter, List.of(topQn), pkg, ntd, topQn, topName);
                }
            }
        }

        // Stable ordering for downstream determinism
        model.types.sort(Comparator.comparing(t -> t.qualifiedName));
        return model;
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

                String extendsType = null;
                List<String> implementsTypes = new ArrayList<>();

                if (td instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
                    if (!cid.getExtendedTypes().isEmpty()) {
                        extendsType = resolveTypeRef(cid.getExtendedTypes().get(0), ctx, nestedByOuter, nestedScopeChain, model, qn, "extends");
                    }
                    for (ClassOrInterfaceType it : cid.getImplementedTypes()) {
                        implementsTypes.add(resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
                    }
                } else if (td instanceof EnumDeclaration) {
                    EnumDeclaration ed = (EnumDeclaration) td;
                    // enums can implement interfaces
                    for (ClassOrInterfaceType it : ed.getImplementedTypes()) {
                        implementsTypes.add(resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
                    }
                }

                List<JField> fields = new ArrayList<>();
                List<JMethod> methods = new ArrayList<>();
                List<String> enumLiterals = new ArrayList<>();

                // Enum literals
                if (td instanceof EnumDeclaration) {
                    EnumDeclaration ed = (EnumDeclaration) td;
                    for (EnumConstantDeclaration ecd : ed.getEntries()) {
                        enumLiterals.add(ecd.getNameAsString());
                    }
                }

                // Fields
                for (BodyDeclaration<?> member : getMembers(td)) {
                        if (member instanceof FieldDeclaration) {
                            FieldDeclaration fd = (FieldDeclaration) member;
                            JVisibility fVis = visibilityOf(fd);
                            boolean fStatic = fd.isStatic();
                            boolean fFinal = fd.isFinal();
                            for (VariableDeclarator var : fd.getVariables()) {
                                String fType = resolveTypeRef(var.getType(), ctx, nestedByOuter, nestedScopeChain, model, qn, "field '" + var.getNameAsString() + "'");
                                fields.add(new JField(var.getNameAsString(), fType, fVis, fStatic, fFinal));
                            }
                        } else if (member instanceof ConstructorDeclaration) {
                            ConstructorDeclaration cd = (ConstructorDeclaration) member;
                            methods.add(extractConstructor(cd, ctx, nestedByOuter, nestedScopeChain, model, qn));
                        } else if (member instanceof MethodDeclaration) {
                            MethodDeclaration md = (MethodDeclaration) member;
                            methods.add(extractMethod(md, ctx, nestedByOuter, nestedScopeChain, model, qn));
                        }
                    }

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
                fields,
                methods,
                enumLiterals
        ));
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
                                              ImportContext ctx,
                                              Map<String, Map<String, String>> nestedByOuter,
                                              List<String> nestedScopeChain,
                                              JModel model,
                                              String ownerQn) {
        JVisibility vis = visibilityOf(cd);
        boolean isStatic = false;
        boolean isAbstract = false;
        List<JParam> params = new ArrayList<>();
        for (Parameter p : cd.getParameters()) {
            String pType = resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "ctor '" + cd.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            params.add(new JParam(p.getNameAsString(), pType));
        }
        return new JMethod(cd.getNameAsString(), "", vis, isStatic, isAbstract, true, params);
    }

    private static JMethod extractMethod(MethodDeclaration md,
                                         ImportContext ctx,
                                         Map<String, Map<String, String>> nestedByOuter,
                                         List<String> nestedScopeChain,
                                         JModel model,
                                         String ownerQn) {
        JVisibility vis = visibilityOf(md);
        boolean isStatic = md.isStatic();
        boolean isAbstract = md.isAbstract();
        String ret = resolveTypeRef(md.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "method '" + md.getNameAsString() + "' return");
        List<JParam> params = new ArrayList<>();
        for (Parameter p : md.getParameters()) {
            String pType = resolveTypeRef(p.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, "method '" + md.getNameAsString() + "' param '" + p.getNameAsString() + "'");
            params.add(new JParam(p.getNameAsString(), pType));
        }
        return new JMethod(md.getNameAsString(), ret, vis, isStatic, isAbstract, false, params);
    }

    private static String resolveTypeRef(Type t,
                                         ImportContext ctx,
                                         Map<String, Map<String, String>> nestedByOuter,
                                         List<String> nestedScopeChain,
                                         JModel model,
                                         String fromQn,
                                         String where) {
        // Preserve full textual form, but resolve the outer-most type name where possible.
        String rendered = renderType(t);
        // Extract base names to attempt resolution (e.g. List<String> -> List, Map<K,V> -> Map)
        Set<String> baseNames = TypeNameUtil.extractBaseTypeNames(rendered);

        // Try to resolve the "main" base name first (best-effort)
        String primary = TypeNameUtil.primaryBaseName(rendered);
        if (primary == null) {
            return rendered;
        }

        String resolvedPrimary = resolveWithNestedScope(primary, ctx, nestedByOuter, nestedScopeChain);
        if (resolvedPrimary == null) {
            // If primary is qualified already, treat as external reference.
            if (primary.contains(".")) {
                model.externalTypeRefs.add(new UnresolvedTypeRef(primary, fromQn, where));
                return rendered;
            }
            // primitives, void, var are not unresolved
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

        // Record other base names (e.g. generic args) as unresolved if they are reference types and not resolvable
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

        // Replace only the primary base name in the rendered form, keeping generics/arrays intact.
        return TypeNameUtil.replacePrimaryBaseName(rendered, primary, resolvedPrimary);
    }

    private static String resolveTypeRef(ClassOrInterfaceType t,
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
     *
     * Supported cases:
     * - Inside Outer: reference "Inner" -> "pkg.Outer.Inner" if such nested type exists.
     * - Inside Inner: reference "Sibling" -> "pkg.Outer.Sibling" (enclosing scope).
     * - Reference "Outer.Inner" (unqualified) -> "pkg.Outer.Inner" if present in project types.
     */
    private static String resolveWithNestedScope(String typeName,
                                                 ImportContext ctx,
                                                 Map<String, Map<String, String>> nestedByOuter,
                                                 List<String> nestedScopeChain) {
        if (typeName == null || typeName.isBlank()) return null;

        // If simple, try nested member types in lexical/enclosing scope first.
        if (!typeName.contains(".")) {
            if (nestedScopeChain != null) {
                for (String scopeQn : nestedScopeChain) {
                    if (scopeQn == null || scopeQn.isBlank()) continue;
                    Map<String, String> m = nestedByOuter.get(scopeQn);
                    if (m == null) continue;
                    String hit = m.get(typeName);
                    if (hit != null) return hit;
                }
            }
            return ctx.resolve(typeName);
        }

        // If qualified but not fully-qualified with a package, try resolving the first segment.
        // 1) Let ImportContext handle: exact project type, or currentPackage + name.
        String direct = ctx.resolve(typeName);
        if (direct != null) return direct;

        // 2) Resolve first segment via imports/package, then append remainder and see if it's a project type.
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
        return null;
    }

    private static String renderType(Type t) {
        if (t == null) return "java.lang.Object";
        if (t instanceof VoidType) return "void";
        return t.toString();
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

    private static final class ImportContext {
        final String currentPackage;
        final Set<String> projectQualifiedTypes;
        final Map<String, String> explicitImportsBySimple = new HashMap<>();
        final List<String> wildcardImports = new ArrayList<>();

        private ImportContext(String currentPackage, Set<String> projectQualifiedTypes) {
            this.currentPackage = currentPackage == null ? "" : currentPackage;
            this.projectQualifiedTypes = projectQualifiedTypes;
        }

        static ImportContext from(CompilationUnit cu, String pkg, Set<String> projectQualifiedTypes) {
            ImportContext ctx = new ImportContext(pkg, projectQualifiedTypes);
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration id : imports) {
                if (id.isStatic()) continue;
                String name = id.getNameAsString();
                if (id.isAsterisk()) {
                    ctx.wildcardImports.add(name);
                } else {
                    String simple = name.substring(name.lastIndexOf('.') + 1);
                    ctx.explicitImportsBySimple.put(simple, name);
                }
            }
            return ctx;
        }

        String resolve(String typeName) {
            if (typeName == null || typeName.isBlank()) return null;
            // If already qualified, only "resolve" if it's a project type; else return null (external)
            if (typeName.contains(".")) {
                if (projectQualifiedTypes.contains(typeName)) return typeName;
                // Support nested member types referenced as Outer.Inner within the same package
                if (!currentPackage.isBlank()) {
                    String cand = currentPackage + "." + typeName;
                    if (projectQualifiedTypes.contains(cand)) return cand;
                }
                return null;
            }
            // same package
            String candidateSamePkg = currentPackage.isBlank() ? typeName : currentPackage + "." + typeName;
            if (projectQualifiedTypes.contains(candidateSamePkg)) return candidateSamePkg;

            // explicit import
            String exp = explicitImportsBySimple.get(typeName);
            if (exp != null && projectQualifiedTypes.contains(exp)) return exp;

            // wildcard imports
            for (String wi : wildcardImports) {
                String cand = wi + "." + typeName;
                if (projectQualifiedTypes.contains(cand)) return cand;
            }
            return null;
        }

/**
 * Try to qualify a non-project type name using imports/wildcards/java.lang.
 * Returns a qualified name if one can be inferred, otherwise null.
 */
String qualifyExternal(String typeName) {
    if (typeName == null || typeName.isBlank()) return null;
    if (typeName.contains(".")) return typeName;

    // explicit import (even if not a project type)
    String exp = explicitImportsBySimple.get(typeName);
    if (exp != null) return exp;

    // java.lang is implicitly imported
    if (TypeNameUtil.isJavaLangImplicit(typeName)) {
        return "java.lang." + typeName;
    }

    // wildcard imports (deterministic order)
    if (!wildcardImports.isEmpty()) {
        List<String> sorted = new ArrayList<>(wildcardImports);
        sorted.sort(String::compareTo);
        return sorted.get(0) + "." + typeName;
    }
    return null;
}

    }

    /**
     * Simple type-string utilities for baseline resolution.
     */
    static final class TypeNameUtil {
        private static final Set<String> JAVA_LANG_IMPLICIT = Set.of(
        "String","Object","Class","Throwable","Exception","RuntimeException","Error",
        "Boolean","Byte","Short","Integer","Long","Float","Double","Character",
        "Void","Number","Enum","Iterable","Comparable","CharSequence"
);

static boolean isJavaLangImplicit(String simple) {
    return simple != null && JAVA_LANG_IMPLICIT.contains(simple);
}

private static final Set<String> PRIMITIVES = Set.of(
                "byte","short","int","long","float","double","boolean","char","void"
        );

        static boolean isNonReferenceType(String name) {
            return name == null || name.isBlank() || PRIMITIVES.contains(name) || "var".equals(name);
        }

        static String primaryBaseName(String rendered) {
            if (rendered == null) return null;
            String s = rendered.trim();
            if (s.isEmpty()) return null;

            // strip annotations in type (best-effort)
            s = s.replaceAll("@[A-Za-z0-9_$.]+\s*", "");

            // remove array suffixes
            while (s.endsWith("[]")) s = s.substring(0, s.length() - 2).trim();

            // remove generic args
            int gen = s.indexOf('<');
            if (gen >= 0) s = s.substring(0, gen).trim();

            // handle wildcard bounds (? extends X)
            if (s.startsWith("?")) {
                String[] parts = s.split("\s+");
                if (parts.length >= 3) return parts[2].trim();
                return null;
            }

            // keep qualified name if present (foo.bar.Baz)
            return s;
        }

        static Set<String> extractBaseTypeNames(String rendered) {
            if (rendered == null) return Set.of();
            // very lightweight tokenizer: find identifiers possibly qualified separated by dots
            // skip primitives and keywords handled later
            Set<String> out = new LinkedHashSet<>();
            String s = rendered.replaceAll("@[A-Za-z0-9_$.]+\\s*", ""); // strip annotations
            // remove punctuation that breaks identifiers but keep dots
            s = s.replaceAll("[<>,?\\[\\]\\(\\)\\{\\}:]", " ");
            for (String token : s.split("\\s+")) {
                if (token.isBlank()) continue;
                // tokens may include bounds keywords extends/super
                if ("extends".equals(token) || "super".equals(token)) continue;
                // token might be qualified name or simple
                if (token.matches("[A-Za-z_][A-Za-z0-9_$.]*")) out.add(token);
            }
            return out;
        }

        static String replacePrimaryBaseName(String rendered, String primary, String replacement) {
            if (rendered == null || primary == null || replacement == null) return rendered;
            // Replace the first occurrence of the primary base name as a token boundary.
            // This is best-effort; EMF/UML export step will do more robust handling later.
            return rendered.replaceFirst("\\b" + java.util.regex.Pattern.quote(primary) + "\\b", java.util.regex.Matcher.quoteReplacement(replacement));
        }
    }
}
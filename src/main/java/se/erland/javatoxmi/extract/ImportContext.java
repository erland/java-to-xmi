package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.util.*;

/**
 * Import context and best-effort name qualification.
 */
final class ImportContext {
    final String currentPackage;
    final Set<String> projectQualifiedTypes;
    final Map<String, String> explicitImportsBySimple = new HashMap<>();
    final List<String> wildcardImports = new ArrayList<>();

    // java.lang contains a small set of commonly used annotations that are implicitly available
    // without imports. We treat these as a best-effort qualification target for annotation names.
    private static final Set<String> JAVA_LANG_ANNOTATIONS = Set.of(
            "Deprecated",
            "Override",
            "SuppressWarnings",
            "SafeVarargs",
            "FunctionalInterface"
    );

    private ImportContext(String currentPackage, Set<String> projectQualifiedTypes) {
        this.currentPackage = currentPackage == null ? "" : currentPackage;
        this.projectQualifiedTypes = projectQualifiedTypes;
    }

    static ImportContext from(CompilationUnit cu, String pkg, Set<String> projectQualifiedTypes) {
        ImportContext ctx = new ImportContext(pkg, projectQualifiedTypes);
        if (cu != null) {
            for (ImportDeclaration id : cu.getImports()) {
                if (id == null) continue;
                if (id.isAsterisk()) {
                    String wi = id.getNameAsString();
                    if (wi != null && !wi.isBlank()) ctx.wildcardImports.add(wi);
                } else {
                    String qn = id.getNameAsString();
                    if (qn == null || qn.isBlank()) continue;
                    String simple = qn.substring(qn.lastIndexOf('.') + 1);
                    ctx.explicitImportsBySimple.put(simple, qn);
                }
            }
        }
        return ctx;
    }

    /** Resolve a type simple name to a project-qualified name where possible. */
        /** Resolve a type simple name (or dotted nested name) to a project-qualified name where possible. */
    String resolve(String typeName) {
        if (typeName == null || typeName.isBlank()) return null;

        // Normalize Java binary nested name separators ($) to source-style dots.
        String tn = typeName.indexOf('$') >= 0 ? typeName.replace('$', '.') : typeName;

        // Already qualified and exists in project
        if (tn.contains(".") && projectQualifiedTypes.contains(tn)) return tn;

        // Same package (works also for dotted names like Outer.Inner)
        String cand = currentPackage == null || currentPackage.isBlank() ? tn : currentPackage + "." + tn;
        if (projectQualifiedTypes.contains(cand)) return cand;

        // If dotted (e.g. Outer.Inner) and Outer is explicitly imported, qualify the chain.
        if (tn.contains(".")) {
            int dot = tn.indexOf('.');
            String head = tn.substring(0, dot);
            String tail = tn.substring(dot + 1);
            if (!head.isBlank() && !tail.isBlank()) {
                String expHead = explicitImportsBySimple.get(head);
                if (expHead != null) {
                    String qn = expHead + "." + tail;
                    if (projectQualifiedTypes.contains(qn)) return qn;
                }
            }
        }

        // Explicit import (simple name)
        String exp = explicitImportsBySimple.get(tn);
        if (exp != null && projectQualifiedTypes.contains(exp)) return exp;

        // Wildcard imports (also supports dotted names)
        for (String wi : wildcardImports) {
            if (wi == null || wi.isBlank()) continue;
            String w = wi + "." + tn;
            if (projectQualifiedTypes.contains(w)) return w;
        }
        return null;
    }

    /** Best-effort qualification for annotation names. */
    String qualifyAnnotation(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return null;
        // explicit import
        String exp = explicitImportsBySimple.get(simpleName);
        if (exp != null) return exp;

        // java.lang built-ins
        if (JAVA_LANG_ANNOTATIONS.contains(simpleName)) return "java.lang." + simpleName;

        // wildcard imports: we don't know which is correct, but return the first
        for (String wi : wildcardImports) {
            if (wi == null || wi.isBlank()) continue;
            return wi + "." + simpleName;
        }

        // same package best-effort
        if (currentPackage != null && !currentPackage.isBlank()) return currentPackage + "." + simpleName;
        return null;
    }

    /** Best-effort qualification for external (non-project) types. */
    String qualifyExternal(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return null;
        String exp = explicitImportsBySimple.get(simpleName);
        if (exp != null) return exp;
        for (String wi : wildcardImports) {
            if (wi == null || wi.isBlank()) continue;
            return wi + "." + simpleName;
        }
        // Try java.lang
        return "java.lang." + simpleName;
    }
}

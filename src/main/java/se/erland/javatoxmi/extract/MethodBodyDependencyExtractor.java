package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VarType;
import se.erland.javatoxmi.model.JModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort extraction of conservative dependencies from method/constructor bodies.
 *
 * <p>This intentionally does not use symbol solving. It relies on:
 * <ul>
 *   <li>Known project types (ImportContext + nested scope),</li>
 *   <li>Declared local variable/parameter types,</li>
 *   <li>"this.field" where the field type is known.</li>
 * </ul>
 * </p>
 */
final class MethodBodyDependencyExtractor {

    private MethodBodyDependencyExtractor() {}

    static Set<String> extract(MethodDeclaration md,
                               ImportContext ctx,
                               Map<String, Map<String, String>> nestedByOuter,
                               List<String> nestedScopeChain,
                               JModel model,
                               String ownerQn,
                               Map<String, String> fieldTypeByName) {
        if (md == null) return Set.of();
        return extractBody(md, md.getBody().orElse(null), md.getParameters(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, fieldTypeByName);
    }

    static Set<String> extract(ConstructorDeclaration cd,
                               ImportContext ctx,
                               Map<String, Map<String, String>> nestedByOuter,
                               List<String> nestedScopeChain,
                               JModel model,
                               String ownerQn,
                               Map<String, String> fieldTypeByName) {
        if (cd == null) return Set.of();
        return extractBody(cd, cd.getBody(), cd.getParameters(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn, fieldTypeByName);
    }

    static List<String> sortedNormalized(Set<String> deps) {
        if (deps == null || deps.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(deps);
        Collections.sort(out);
        return out;
    }

    private static Set<String> extractBody(Node callable,
                                               BlockStmt body,
                                               List<Parameter> parameters,
                                               ImportContext ctx,
                                               Map<String, Map<String, String>> nestedByOuter,
                                               List<String> nestedScopeChain,
                                               JModel model,
                                               String ownerQn,
                                               Map<String, String> fieldTypeByName) {
        if (callable == null) return Set.of();
        if (body == null) return Set.of();

        // Local symbol table: var name -> resolved type string (may include generics; normalized later)
        Map<String, String> locals = new HashMap<>();
        if (parameters != null) {
            for (Parameter p : parameters) {
            if (p == null) continue;
            String n = p.getNameAsString();
            if (n == null || n.isBlank()) continue;
            Type pt = p.getType();
            if (pt instanceof VarType) continue;
            String r = TypeResolver.resolveTypeRef(pt, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "param '" + n + "' (body)");
            if (r != null && !r.isBlank()) locals.put(n, r);
            }
        }

        // Collect local variable declarations before resolving calls. This is best-effort; shadowing is ignored.
        body.walk(VariableDeclarator.class, vd -> {
            if (vd == null) return;
            String n = vd.getNameAsString();
            if (n == null || n.isBlank()) return;
            Type t = vd.getType();
            if (t instanceof VarType) return;
            String r = TypeResolver.resolveTypeRef(t, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "local '" + n + "' (body)");
            if (r != null && !r.isBlank()) locals.put(n, r);
        });

        // foreach variables
        body.walk(ForEachStmt.class, fe -> {
            if (fe == null) return;
            if (fe.getVariable() == null) return;
            if (fe.getVariable().getVariables().isEmpty()) return;
            for (VariableDeclarator vd : fe.getVariable().getVariables()) {
                if (vd == null) continue;
                String n = vd.getNameAsString();
                Type t = vd.getType();
                if (t instanceof VarType) continue;
                String r = TypeResolver.resolveTypeRef(t, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                        "foreach '" + n + "' (body)");
                if (r != null && !r.isBlank()) locals.put(n, r);
            }
        });

        // catch parameters
        body.walk(CatchClause.class, cc -> {
            if (cc == null || cc.getParameter() == null) return;
            String n = cc.getParameter().getNameAsString();
            Type t = cc.getParameter().getType();
            if (t instanceof VarType) return;
            String r = TypeResolver.resolveTypeRef(t, ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "catch '" + n + "' (body)");
            if (r != null && !r.isBlank()) locals.put(n, r);
        });

        Set<String> deps = new HashSet<>();

        // 1) new Foo(...)
        body.walk(ObjectCreationExpr.class, oce -> {
            if (oce == null || oce.getType() == null) return;
            String r = TypeResolver.resolveTypeRef(oce.getType(), ctx, nestedByOuter, nestedScopeChain, model, ownerQn,
                    "new '" + oce.getType().getNameAsString() + "' (body)");
            String dep = normalizeDependencyName(r);
            if (dep != null && !dep.isBlank()) deps.add(dep);
        });

        // 2) Method calls with resolvable scope
        body.walk(MethodCallExpr.class, mc -> {
            if (mc == null) return;
            Expression scope = mc.getScope().orElse(null);
            if (scope == null) return;

            // x.foo() where x is local/param
            if (scope instanceof NameExpr) {
                String n = ((NameExpr) scope).getNameAsString();
                if (n != null && locals.containsKey(n)) {
                    String dep = normalizeDependencyName(locals.get(n));
                    if (dep != null && !dep.isBlank()) deps.add(dep);
                    return;
                }

                // Foo.bar() where Foo is a type name
                if (n != null && !n.isBlank()) {
                    String typeQn = TypeResolver.resolveWithNestedScope(n, ctx, nestedByOuter, nestedScopeChain);
                    String dep = normalizeDependencyName(typeQn);
                    if (dep != null && !dep.isBlank()) deps.add(dep);
                }
                return;
            }

            // this.field.foo()
            if (scope instanceof FieldAccessExpr) {
                FieldAccessExpr fa = (FieldAccessExpr) scope;
                if (fa.getScope() instanceof ThisExpr) {
                    String fn = fa.getNameAsString();
                    if (fn != null && fieldTypeByName != null) {
                        String ft = fieldTypeByName.get(fn);
                        String dep = normalizeDependencyName(ft);
                        if (dep != null && !dep.isBlank()) deps.add(dep);
                    }
                }
                return;
            }

            // If scope is more complex (method chain, cast, etc.), we skip in this conservative pass.
        });

        // Remove self-deps (can happen for constructors/new of same type or fluent patterns)
        deps.remove(ownerQn);
        return deps;
    }

    private static String normalizeDependencyName(String typeRef) {
        if (typeRef == null) return null;
        String s = typeRef.trim();
        if (s.isEmpty()) return null;
        // Strip generics
        int lt = s.indexOf('<');
        if (lt >= 0) s = s.substring(0, lt).trim();
        // Strip array suffix
        while (s.endsWith("[]")) {
            s = s.substring(0, s.length() - 2).trim();
        }
        // Some resolver paths may return empty; keep conservative.
        if (s.isEmpty()) return null;
        return s;
    }
}

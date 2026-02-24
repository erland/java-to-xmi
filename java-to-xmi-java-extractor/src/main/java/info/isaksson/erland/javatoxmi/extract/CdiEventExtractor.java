package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JRuntimeRelation;

import java.util.*;

/**
 * Best-effort extraction of CDI event semantics:
 * <ul>
 *   <li>Observer methods: {@code @Observes}/{@code @ObservesAsync}</li>
 *   <li>Event firing via injected {@code Event<T>} variables calling {@code fire}/{@code fireAsync}</li>
 * </ul>
 *
 * <p>Emits {@link JRuntimeRelation} entries that are later rendered as stereotyped UML dependencies.
 * We intentionally keep this conservative and deterministic; no symbol solving is used.</p>
 */
final class CdiEventExtractor {

    // Runtime tag keys (shared convention with IrRuntime).
    static final String TAG_PREFIX = "runtime.";
    static final String TAG_QUALIFIERS = TAG_PREFIX + "qualifiers";
    static final String TAG_ASYNC = TAG_PREFIX + "async";

    // Runtime stereotypes (shared convention with IrRuntime).
    static final String ST_FIRES_EVENT = "FiresEvent";
    static final String ST_OBSERVES_EVENT = "ObservesEvent";

    // CDI types
    private static final String CDI_EVENT_1 = "javax.enterprise.event.Event";
    private static final String CDI_EVENT_2 = "jakarta.enterprise.event.Event";

    private static final String OBSERVES_1 = "javax.enterprise.event.Observes";
    private static final String OBSERVES_2 = "jakarta.enterprise.event.Observes";
    private static final String OBSERVES_ASYNC_1 = "javax.enterprise.event.ObservesAsync";
    private static final String OBSERVES_ASYNC_2 = "jakarta.enterprise.event.ObservesAsync";

    void extract(JModel model, List<ParsedUnit> units, ProjectTypeIndex index) {
        if (model == null || units == null || units.isEmpty() || index == null) return;

        List<JRuntimeRelation> out = new ArrayList<>();

        for (ParsedUnit pu : units) {
            if (pu == null || pu.cu == null) continue;
            CompilationUnit cu = pu.cu;
            String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            ImportContext ctx = ImportContext.from(cu, pkg, index.projectTypeQualifiedNames);

            // Walk all top-level and nested classes
            for (ClassOrInterfaceDeclaration cid : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (cid.isLocalClassDeclaration()) continue;

                String typePath = typePathFromTop(cid);
                if (typePath == null) continue;
                String typeQn = TypeExtractionEngine.qualifiedName(pkg, typePath);

                List<String> nestedScopeChain = nestedScopeChain(pkg, typePath);

                // Map injected Event<T> variables visible in this class: fieldName/paramName -> eventTypeQn
                Map<String, String> eventVars = new HashMap<>();
                Map<String, String> eventVarQualifiers = new HashMap<>();

                for (FieldDeclaration fd : cid.getFields()) {
                    for (var v : fd.getVariables()) {
                        if (v == null) continue;
                        String name = v.getNameAsString();
                        if (name == null || name.isBlank()) continue;
                        ClassOrInterfaceType cit = v.getType().isClassOrInterfaceType() ? v.getType().asClassOrInterfaceType() : null;
                        String evtType = eventTypeArgumentIfCdiEvent(cit, ctx, index, model, typeQn, nestedScopeChain);
                        if (evtType != null) {
                            eventVars.put(name, evtType);
                            String qs = qualifiersFromAnnotations(fd);
                            if (!qs.isBlank()) eventVarQualifiers.put(name, qs);
                        }
                    }
                }

                for (MethodDeclaration md : cid.getMethods()) {
                    if (md == null) continue;

                    // Add Event<T> variables from parameters as well (common with constructor/method injection)
                    for (Parameter p : md.getParameters()) {
                        if (p == null) continue;
                        String pn = p.getNameAsString();
                        if (pn == null || pn.isBlank()) continue;
                        ClassOrInterfaceType cit = p.getType().isClassOrInterfaceType() ? p.getType().asClassOrInterfaceType() : null;
                        String evtType = eventTypeArgumentIfCdiEvent(cit, ctx, index, model, typeQn, nestedScopeChain);
                        if (evtType != null) {
                            eventVars.put(pn, evtType);
                            String qs = qualifiersFromAnnotations(p);
                            if (!qs.isBlank()) eventVarQualifiers.put(pn, qs);
                        }
                    }

                    // Observer params
                    for (Parameter p : md.getParameters()) {
                        if (p == null) continue;
                        ObsInfo obs = observesInfo(p, ctx);
                        if (!obs.isObserver) continue;

                        String eventType = TypeResolver.resolveTypeRef(p.getType(), ctx, index.nestedByOuter, nestedScopeChain, model, typeQn, "@Observes param");
                        if (eventType == null || eventType.isBlank()) continue;

                        Map<String, String> tags = new LinkedHashMap<>();
                        String qs = qualifiersFromAnnotations(p);
                        if (!qs.isBlank()) tags.put(TAG_QUALIFIERS, qs);
                        if (obs.isAsync) tags.put(TAG_ASYNC, "true");

                        out.add(new JRuntimeRelation(
                                null,
                                typeQn,
                                TypeNameUtil.primaryBaseName(eventType) != null ? TypeNameUtil.primaryBaseName(eventType) : eventType,
                                null,
                                ST_OBSERVES_EVENT,
                                tags
                        ));
                    }

                    // Event firing (fire/fireAsync on known Event<T> variable)
                    for (MethodCallExpr call : md.findAll(MethodCallExpr.class)) {
                        if (call == null) continue;
                        String n = call.getNameAsString();
                        if (!"fire".equals(n) && !"fireAsync".equals(n)) continue;
                        Optional<Expression> scope = call.getScope();
                        if (scope.isEmpty()) continue;
                        Expression s = scope.get();
                        if (!(s instanceof NameExpr)) continue;
                        String var = ((NameExpr) s).getNameAsString();
                        String eventType = eventVars.get(var);
                        if (eventType == null) continue;

                        Map<String, String> tags = new LinkedHashMap<>();
                        if ("fireAsync".equals(n)) tags.put(TAG_ASYNC, "true");
                        String qs = eventVarQualifiers.get(var);
                        if (qs != null && !qs.isBlank()) tags.put(TAG_QUALIFIERS, qs);

                        out.add(new JRuntimeRelation(
                                null,
                                typeQn,
                                TypeNameUtil.primaryBaseName(eventType) != null ? TypeNameUtil.primaryBaseName(eventType) : eventType,
                                null,
                                ST_FIRES_EVENT,
                                tags
                        ));
                    }
                }
            }
        }

        // Deterministic ordering
        out.sort((a, b) -> {
            String as = a == null ? "" : (a.sourceQualifiedName == null ? "" : a.sourceQualifiedName);
            String bs = b == null ? "" : (b.sourceQualifiedName == null ? "" : b.sourceQualifiedName);
            int c = as.compareTo(bs);
            if (c != 0) return c;
            String at = a == null ? "" : (a.targetQualifiedName == null ? "" : a.targetQualifiedName);
            String bt = b == null ? "" : (b.targetQualifiedName == null ? "" : b.targetQualifiedName);
            c = at.compareTo(bt);
            if (c != 0) return c;
            String ast = a == null ? "" : (a.stereotype == null ? "" : a.stereotype);
            String bst = b == null ? "" : (b.stereotype == null ? "" : b.stereotype);
            return ast.compareTo(bst);
        });

        model.runtimeRelations.addAll(out);
    }

    private static String eventTypeArgumentIfCdiEvent(ClassOrInterfaceType t,
                                                     ImportContext ctx,
                                                     ProjectTypeIndex index,
                                                     JModel model,
                                                     String fromQn,
                                                     List<String> nestedScopeChain) {
        if (t == null) return null;
        String simple = t.getNameAsString();
        if (simple == null || simple.isBlank()) return null;

        String qn;
        if (simple.contains(".")) {
            qn = simple;
        } else {
            String ext = ctx.qualifyExternal(simple);
            qn = ext != null ? ext : simple;
        }
        if (!CDI_EVENT_1.equals(qn) && !CDI_EVENT_2.equals(qn)) return null;

        if (t.getTypeArguments().isEmpty()) return null;
        var args = t.getTypeArguments().get();
        if (args.size() != 1) return null;
        Type arg = args.get(0);
        if (arg == null) return null;
        return TypeResolver.resolveTypeRef(arg, ctx, index.nestedByOuter, nestedScopeChain, model, fromQn, "Event<T>");
    }

    private static ObsInfo observesInfo(Parameter p, ImportContext ctx) {
        if (p == null) return new ObsInfo(false, false);
        boolean sync = hasAnnotation(p, "Observes", OBSERVES_1, OBSERVES_2, ctx);
        boolean async = hasAnnotation(p, "ObservesAsync", OBSERVES_ASYNC_1, OBSERVES_ASYNC_2, ctx);
        return new ObsInfo(sync || async, async);
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> n,
                                         String simple,
                                         String qn1,
                                         String qn2,
                                         ImportContext ctx) {
        if (n == null) return false;
        for (AnnotationExpr a : n.getAnnotations()) {
            if (a == null) continue;
            String name = a.getNameAsString();
            if (name == null) continue;
            if (name.equals(simple)) return true;
            if (name.equals(qn1) || name.equals(qn2)) return true;
            if (!name.contains(".")) {
                String q = ctx.qualifyAnnotation(name);
                if (qn1.equals(q) || qn2.equals(q)) return true;
            }
        }
        return false;
    }

    private static String qualifiersFromAnnotations(NodeWithAnnotations<?> n) {
        if (n == null) return "";
        List<String> qs = new ArrayList<>();
        for (AnnotationExpr a : n.getAnnotations()) {
            if (a == null) continue;
            String name = a.getNameAsString();
            if (name == null || name.isBlank()) continue;
            // skip known CDI observer annotations themselves
            if ("Observes".equals(name) || "ObservesAsync".equals(name)) continue;
            if (name.endsWith(".Observes") || name.endsWith(".ObservesAsync")) continue;
            // also skip injection marker
            if ("Inject".equals(name) || name.endsWith(".Inject")) continue;
            qs.add(name);
        }
        if (qs.isEmpty()) return "";
        qs.sort(String::compareTo);
        return String.join(",", qs);
    }

    private static String typePathFromTop(ClassOrInterfaceDeclaration cid) {
        if (cid == null) return null;
        List<String> parts = new ArrayList<>();
        Node cur = cid;
        while (cur != null) {
            if (cur instanceof ClassOrInterfaceDeclaration) {
                String n = ((ClassOrInterfaceDeclaration) cur).getNameAsString();
                if (n != null && !n.isBlank()) parts.add(n);
            }
            cur = cur.getParentNode().orElse(null);
        }
        if (parts.isEmpty()) return null;
        Collections.reverse(parts);
        return String.join(".", parts);
    }

    private static List<String> nestedScopeChain(String pkg, String typePath) {
        if (typePath == null || typePath.isBlank()) return List.of();
        String[] segs = typePath.split("\\.");
        List<String> chain = new ArrayList<>();
        String cur = "";
        for (int i = 0; i < segs.length; i++) {
            cur = cur.isEmpty() ? segs[i] : (cur + "." + segs[i]);
            chain.add(TypeExtractionEngine.qualifiedName(pkg, cur));
        }
        return chain;
    }

    private record ObsInfo(boolean isObserver, boolean isAsync) {}
}

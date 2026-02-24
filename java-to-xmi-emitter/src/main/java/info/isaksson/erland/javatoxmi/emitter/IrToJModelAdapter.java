package info.isaksson.erland.javatoxmi.emitter;

import info.isaksson.erland.javatoxmi.ir.*;
import info.isaksson.erland.javatoxmi.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Adapter that maps the cross-language IR model into the existing Java-centric JModel
 * so the current UML builder + XMI writer can be reused.
 *
 * <p>Best-effort mapping:</p>
 * <ul>
 *   <li>IR classifiers -> JType</li>
 *   <li>attributes -> JField</li>
 *   <li>operations -> JMethod</li>
 *   <li>GENERALIZATION/REALIZATION relations -> extends/implements</li>
 *   <li>ASSOCIATION/AGGREGATION/COMPOSITION relations -> synthetic fields on source</li>
 *   <li>DEPENDENCY/RENDER/DI/TEMPLATE_USES/ROUTE_TO relations -> methodBodyTypeDependencies on source</li>
 * </ul>
 */
final class IrToJModelAdapter {

    JModel adapt(IrModel ir, EmitterOptions opts) {
        if (ir == null) throw new IllegalArgumentException("ir model must not be null");
        if (opts == null) opts = EmitterOptions.defaults("model");

        JModel jm = new JModel(Path.of("."), List.of());

        Map<String, IrClassifier> byId = new HashMap<>();
        for (IrClassifier c : ir.classifiers) {
            if (c != null && c.id != null) byId.put(c.id, c);
        }


        Map<String, IrPackage> packagesById = new HashMap<>();
        for (IrPackage p : safe(ir.packages)) {
            if (p != null && p.id != null) packagesById.put(p.id, p);
        }

        // Use a mutable builder to set extends/implements/extra relations before creating immutable JType.
        Map<String, MutableType> types = new LinkedHashMap<>();

        for (IrClassifier c : ir.classifiers) {
            if (c == null) continue;

            String qn = nonBlank(c.qualifiedName, c.name, c.id);
            String pkg = resolvePackageName(c, packagesById);
            if (pkg == null || pkg.isBlank()) pkg = packageOf(qn);
            String simple = simpleNameOf(qn, c.name);

            MutableType mt = new MutableType();
            mt.packageName = pkg;
            mt.name = simple;
            mt.qualifiedName = qn;
            mt.kind = mapKind(c.kind);
            mt.visibility = mapVisibility(c.visibility);
            mt.isAbstract = false;
            mt.isStatic = false;
            mt.isFinal = false;

            // Members
            for (IrAttribute a : safe(c.attributes)) {
                if (a == null) continue;
                mt.fields.add(toField(a));
            }
            for (IrOperation o : safe(c.operations)) {
                if (o == null) continue;
                mt.methods.add(toMethod(o));
            }

            // Framework stereotypes/tags are not mapped to Java annotations here (best-effort).
            // They can be carried later via tagged values and/or a future profile builder.

            types.put(c.id != null ? c.id : qn, mt);
        }

        // Relations pass: inheritance + explicit edges
        for (IrRelation r : safe(ir.relations)) {
            if (r == null) continue;
            if (r.sourceId == null || r.targetId == null) continue;

            MutableType src = types.get(r.sourceId);
            IrClassifier tgtC = byId.get(r.targetId);
            if (src == null || tgtC == null) continue;

            String tgtQn = nonBlank(tgtC.qualifiedName, tgtC.name, tgtC.id);

            switch (r.kind == null ? IrRelationKind.DEPENDENCY : r.kind) {
                case GENERALIZATION:
                    // Only keep first; if multiple, last wins (best-effort).
                    src.extendsType = tgtQn;
                    break;
                case REALIZATION:
                    if (!src.implementsTypes.contains(tgtQn)) src.implementsTypes.add(tgtQn);
                    break;
                case ASSOCIATION:
                case AGGREGATION:
                case COMPOSITION:
                    // Represent as a synthetic field so current association builder can emit an association.
                    // Name uses relation name when present, else derives from target simple name.
                    String fname = (r.name != null && !r.name.isBlank())
                            ? r.name.trim()
                            : decapitalize(simpleNameOf(tgtQn, tgtQn));
                    src.fields.add(new JField(fname, tgtQn, JVisibility.PRIVATE, false, false));
                    break;
                case DEPENDENCY:
                case RENDER:
                case DI:
                case TEMPLATE_USES:
                case ROUTE_TO:
                    // Preserve as a runtime relation when it carries runtime semantics (stereotype or runtime.* tags).
                    // IMPORTANT: when we emit a runtime semantic dependency, do NOT also add it to
                    // methodBodyTypeDependencies. Otherwise we end up with two UML Dependencies between the same
                    // client/supplier, and tests/consumers may pick the non-annotated one.
                    boolean isRuntime = isRuntimeSemanticRelation(r);
                    if (isRuntime) {
                        jm.runtimeRelations.add(toRuntimeRelation(r, src.qualifiedName, tgtQn));
                    } else if (opts.includeDependencies) {
                        if (!src.methodBodyTypeDependencies.contains(tgtQn)) src.methodBodyTypeDependencies.add(tgtQn);
                    }
                    break;
                default:
                    // ignore unknown
                    break;
            }
        }

        // Build immutable JTypes
        for (MutableType mt : types.values()) {
            JType jt = new JType(
                    mt.packageName,
                    mt.name,
                    mt.qualifiedName,
                    null,
                    mt.kind,
                    mt.visibility,
                    mt.isAbstract,
                    mt.isStatic,
                    mt.isFinal,
                    mt.extendsType,
                    mt.implementsTypes,
                    List.of(),
                    null,
                    mt.fields,
                    mt.methods,
                    List.of(),
                    mt.methodBodyTypeDependencies
            );
            jm.types.add(jt);
        }

        jm.types.sort(Comparator.comparing(t -> t.qualifiedName));
        return jm;
    }

    private static boolean isRuntimeSemanticRelation(IrRelation r) {
        if (r == null) return false;
        if (r.stereotypes != null) {
            for (IrStereotype st : r.stereotypes) {
                if (st == null) continue;
                String n = nonBlank(st.name, st.qualifiedName, "");
                if (n != null && !n.isBlank()) return true;
            }
        }
        if (r.taggedValues != null) {
            for (IrTaggedValue tv : r.taggedValues) {
                if (tv == null || tv.key == null) continue;
                if (tv.key.startsWith(IrRuntime.TAG_PREFIX)) return true;
            }
        }
        return false;
    }

    private static JRuntimeRelation toRuntimeRelation(IrRelation r, String srcQn, String tgtQn) {
        String stName = null;
        if (r.stereotypes != null) {
            for (IrStereotype st : r.stereotypes) {
                if (st == null) continue;
                String n = nonBlank(st.name, st.qualifiedName, null);
                if (n != null && !n.isBlank()) { stName = n.trim(); break; }
            }
        }

        Map<String, String> tags = new HashMap<>();
        if (r.taggedValues != null) {
            for (IrTaggedValue tv : r.taggedValues) {
                if (tv == null) continue;
                if (tv.key == null || tv.key.isBlank()) continue;
                if (tv.value == null) continue;
                tags.put(tv.key, tv.value);
            }
        }

        return new JRuntimeRelation(r.id, srcQn, tgtQn, r.name, stName, tags);
    }

    
private static JField toField(IrAttribute a) {
    String typeStr = renderType(a.type);
    TypeRef typeRef = toTypeRef(a.type);
    return new JField(
            nonBlank(a.name, ""),
            typeStr,
            typeRef,
            mapVisibility(a.visibility),
            a.isStatic,
            a.isFinal,
            List.of()
    );
}


    
private static JMethod toMethod(IrOperation o) {
    List<JParam> params = new ArrayList<>();
    for (IrParameter p : safe(o.parameters)) {
        if (p == null) continue;
        params.add(new JParam(nonBlank(p.name, ""), renderType(p.type)));
    }

    String rt = o.isConstructor ? "" : renderType(o.returnType);
    TypeRef rtRef = o.isConstructor ? null : toTypeRef(o.returnType);

    return new JMethod(
            nonBlank(o.name, ""),
            rt,
            rtRef,
            mapVisibility(o.visibility),
            o.isStatic,
            o.isAbstract,
            o.isConstructor,
            params,
            List.of()
    );
}



    private static TypeRef toTypeRef(IrTypeRef tr) {
        if (tr == null) return TypeRef.simple("java.lang.Object", "Object", "java.lang.Object");
        IrTypeRefKind k = tr.kind == null ? IrTypeRefKind.UNKNOWN : tr.kind;

        switch (k) {
            case PRIMITIVE: {
                String n = nonBlank(tr.name, "void");
                return TypeRef.simple(n, n, "");
            }
            case NAMED: {
                String qn = nonBlank(tr.name, "java.lang.Object");
                String simple = simpleNameOf(qn, qn);
                String hint = qn.contains(".") ? qn : "";
                return TypeRef.simple(qn, simple, hint);
            }
            case GENERIC: {
                String base = nonBlank(tr.name, "java.lang.Object");
                String simple = simpleNameOf(base, base);
                String hint = base.contains(".") ? base : "";
                List<TypeRef> args = new ArrayList<>();
                for (IrTypeRef a : safe(tr.typeArgs)) {
                    if (a == null) continue;
                    args.add(toTypeRef(a));
                }
                return TypeRef.param(renderType(tr), simple, hint, args);
            }
            case ARRAY: {
                // Count nested arrays
                int dims = 0;
                IrTypeRef cur = tr;
                while (cur != null && (cur.kind == IrTypeRefKind.ARRAY)) {
                    dims++;
                    cur = cur.elementType;
                }
                TypeRef comp = toTypeRef(cur);
                return TypeRef.array(renderType(tr), comp, dims);
            }
            case UNION:
            case INTERSECTION: {
                // No direct Java equivalent; keep raw string and treat as SIMPLE
                String raw = renderType(tr);
                String simple = simpleNameOf(raw, raw);
                return TypeRef.simple(raw, simple, "");
            }
            case UNKNOWN:
            default:
                return TypeRef.simple("java.lang.Object", "Object", "java.lang.Object");
        }
    }

    private static String renderType(IrTypeRef tr) {
        if (tr == null) return "java.lang.Object";
        IrTypeRefKind k = tr.kind == null ? IrTypeRefKind.UNKNOWN : tr.kind;
        switch (k) {
            case PRIMITIVE:
                return nonBlank(tr.name, "void");
            case NAMED:
                return nonBlank(tr.name, "java.lang.Object");
            case GENERIC:
                String base = nonBlank(tr.name, "java.lang.Object");
                if (tr.typeArgs == null || tr.typeArgs.isEmpty()) return base;
                StringBuilder sb = new StringBuilder(base).append("<");
                for (int i = 0; i < tr.typeArgs.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(renderType(tr.typeArgs.get(i)));
                }
                sb.append(">");
                return sb.toString();
            case ARRAY:
                return renderType(tr.elementType) + "[]";
            case UNION:
            case INTERSECTION:
                // Best-effort: use first alternative.
                if (tr.typeArgs != null && !tr.typeArgs.isEmpty()) return renderType(tr.typeArgs.get(0));
                return "java.lang.Object";
            case UNKNOWN:
            default:
                return "java.lang.Object";
        }
    }

    private static JTypeKind mapKind(IrClassifierKind k) {
        if (k == null) return JTypeKind.CLASS;
        switch (k) {
            case INTERFACE:
                return JTypeKind.INTERFACE;
            case ENUM:
                return JTypeKind.ENUM;
            case CLASS:
            case COMPONENT:
            case SERVICE:
            case MODULE:
            case TYPE_ALIAS:
            default:
                return JTypeKind.CLASS;
        }
    }

    private static JVisibility mapVisibility(IrVisibility v) {
        if (v == null) return JVisibility.PACKAGE_PRIVATE;
        switch (v) {
            case PUBLIC: return JVisibility.PUBLIC;
            case PROTECTED: return JVisibility.PROTECTED;
            case PRIVATE: return JVisibility.PRIVATE;
            case PACKAGE:
            default: return JVisibility.PACKAGE_PRIVATE;
        }
    }


    private static String resolvePackageName(IrClassifier c, Map<String, IrPackage> packagesById) {
        if (c == null || c.packageId == null || c.packageId.isBlank() || packagesById == null) return "";
        List<String> parts = new ArrayList<>();
        String cur = c.packageId;
        int guard = 0;
        while (cur != null && !cur.isBlank() && guard++ < 1000) {
            IrPackage p = packagesById.get(cur);
            if (p == null) break;
            String name = p.name == null ? "" : p.name.trim();
            if (!name.isBlank()) {
                parts.add(name);
            }
            cur = p.parentId;
        }
        if (parts.isEmpty()) return "";
        Collections.reverse(parts);
        // UmlClassifierBuilder creates nested packages by splitting on '.'
        // so we must avoid '.' inside a segment to prevent accidental extra levels.
        for (int i = 0; i < parts.size(); i++) {
            parts.set(i, sanitizePackageSegment(parts.get(i)));
        }
        return String.join(".", parts);
    }

    private static String sanitizePackageSegment(String s) {
        if (s == null) return "";
        // conservative: keep most characters but replace separators that would create misleading nesting
        return s.replace('.', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private static String packageOf(String qn) {
        if (qn == null) return "";
        int idx = qn.lastIndexOf('.');
        if (idx <= 0) return "";
        return qn.substring(0, idx);
    }

    private static String simpleNameOf(String qn, String fallback) {
        if (qn == null || qn.isBlank()) return nonBlank(fallback, "");
        int idx = qn.lastIndexOf('.');
        if (idx >= 0 && idx < qn.length() - 1) return qn.substring(idx + 1);
        return qn;
    }

    private static String nonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toLowerCase(Locale.ROOT);
        if (Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static final class MutableType {
        String packageName = "";
        String name = "";
        String qualifiedName = "";
        JTypeKind kind = JTypeKind.CLASS;
        JVisibility visibility = JVisibility.PACKAGE_PRIVATE;
        boolean isAbstract = false;
        boolean isStatic = false;
        boolean isFinal = false;
        String extendsType = null;
        final List<String> implementsTypes = new ArrayList<>();
        final List<JField> fields = new ArrayList<>();
        final List<JMethod> methods = new ArrayList<>();
        final List<String> methodBodyTypeDependencies = new ArrayList<>();
    }
}

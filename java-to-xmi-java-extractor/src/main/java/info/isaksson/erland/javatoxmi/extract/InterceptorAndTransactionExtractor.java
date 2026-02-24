package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.model.JAnnotationUse;
import info.isaksson.erland.javatoxmi.model.JMethod;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JParam;
import info.isaksson.erland.javatoxmi.model.JRuntimeAnnotation;
import info.isaksson.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort extraction of interceptor and transaction boundary semantics.
 *
 * <p>Outputs {@link JRuntimeAnnotation} entries that the UML builder can annotate onto existing
 * classifiers/operations. Stereotype applications are later injected during XMI writing.</p>
 */
final class InterceptorAndTransactionExtractor {

    // Runtime tag keys (shared convention with IrRuntime)
    static final String TAG_BINDINGS = "runtime.bindings";
    static final String TAG_TRANSACTIONAL = "runtime.transactional";
    static final String TAG_TX_PROPAGATION = "runtime.tx.propagation";
    static final String TAG_TX_READ_ONLY = "runtime.tx.readOnly";
    static final String TAG_TX_ISOLATION = "runtime.tx.isolation";

    // Runtime stereotypes (shared convention with IrRuntime)
    static final String ST_INTERCEPTOR = "Interceptor";
    static final String ST_TRANSACTIONAL = "Transactional";

    // Interceptor annotations
    private static final String JAKARTA_INTERCEPTOR = "jakarta.interceptor.Interceptor";
    private static final String JAVAX_INTERCEPTOR = "javax.interceptor.Interceptor";
    private static final String JAKARTA_AROUND_INVOKE = "jakarta.interceptor.AroundInvoke";
    private static final String JAVAX_AROUND_INVOKE = "javax.interceptor.AroundInvoke";

    // Transaction annotations
    private static final String JAKARTA_TX = "jakarta.transaction.Transactional";
    private static final String JAVAX_TX = "javax.transaction.Transactional";
    private static final String SPRING_TX = "org.springframework.transaction.annotation.Transactional";

    void extract(JModel model) {
        if (model == null || model.types == null || model.types.isEmpty()) return;

        List<JRuntimeAnnotation> out = new ArrayList<>();
        for (JType t : model.types) {
            if (t == null) continue;

            // Interceptor class?
            if (isInterceptorClass(t)) {
                Map<String, String> tags = new LinkedHashMap<>();
                String bindings = detectInterceptorBindings(t.annotations);
                if (!bindings.isBlank()) tags.put(TAG_BINDINGS, bindings);
                out.add(new JRuntimeAnnotation(t.qualifiedName, ST_INTERCEPTOR, tags));
            }

            // Transactional on class?
            JAnnotationUse txClass = firstTxAnnotation(t.annotations);
            if (txClass != null) {
                out.add(new JRuntimeAnnotation(t.qualifiedName, ST_TRANSACTIONAL, txTags(txClass)));
            }

            // Transactional on methods?
            for (JMethod m : t.methods) {
                if (m == null) continue;
                JAnnotationUse txMethod = firstTxAnnotation(m.annotations);
                if (txMethod != null) {
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_TRANSACTIONAL, txTags(txMethod)));
                }
            }
        }

        // Deterministic ordering
        out.sort((a, b) -> {
            String ak = a == null ? "" : (a.targetKey == null ? "" : a.targetKey);
            String bk = b == null ? "" : (b.targetKey == null ? "" : b.targetKey);
            int c = ak.compareTo(bk);
            if (c != 0) return c;
            String as = a == null ? "" : (a.stereotype == null ? "" : a.stereotype);
            String bs = b == null ? "" : (b.stereotype == null ? "" : b.stereotype);
            return as.compareTo(bs);
        });

        model.runtimeAnnotations.addAll(out);
    }

    private static boolean isInterceptorClass(JType t) {
        if (t.annotations == null) return false;
        if (hasAnnotation(t.annotations, "Interceptor", JAKARTA_INTERCEPTOR, JAVAX_INTERCEPTOR)) return true;

        // Fallback: sometimes interceptor may be missing @Interceptor but has @AroundInvoke method.
        for (JMethod m : t.methods) {
            if (m == null) continue;
            if (hasAnnotation(m.annotations, "AroundInvoke", JAKARTA_AROUND_INVOKE, JAVAX_AROUND_INVOKE)) return true;
        }
        return false;
    }

    /**
     * Best-effort binding detection: treat "other" annotations on the interceptor class as bindings.
     * This is conservative and does not attempt meta-annotation resolution (@InterceptorBinding).
     */
    private static String detectInterceptorBindings(List<JAnnotationUse> anns) {
        if (anns == null || anns.isEmpty()) return "";
        List<String> bindings = new ArrayList<>();
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            String sn = a.simpleName == null ? "" : a.simpleName.trim();
            String qn = a.qualifiedName == null ? "" : a.qualifiedName.trim();
            if (sn.isEmpty()) continue;

            // Ignore known non-binding framework annotations
            if (sn.equals("Interceptor") || qn.equals(JAKARTA_INTERCEPTOR) || qn.equals(JAVAX_INTERCEPTOR)) continue;
            if (sn.equals("Priority") || qn.endsWith(".Priority")) continue;
            if (sn.equals("Dependent") || sn.endsWith("Scoped") || sn.endsWith("Scope")) continue;
            if (sn.equals("SuppressWarnings")) continue;

            // Include remaining annotations by simple name
            bindings.add(sn);
        }
        bindings.sort(String::compareTo);
        return String.join(",", bindings);
    }

    private static JAnnotationUse firstTxAnnotation(List<JAnnotationUse> anns) {
        if (anns == null || anns.isEmpty()) return null;
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            String sn = a.simpleName == null ? "" : a.simpleName.trim();
            String qn = a.qualifiedName == null ? "" : a.qualifiedName.trim();
            if ("Transactional".equals(sn)) return a;
            if (qn.equals(JAKARTA_TX) || qn.equals(JAVAX_TX) || qn.equals(SPRING_TX)) return a;
        }
        return null;
    }

    private static Map<String, String> txTags(JAnnotationUse tx) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(TAG_TRANSACTIONAL, "true");
        if (tx == null || tx.values == null || tx.values.isEmpty()) return tags;

        // propagation / value
        String prop = firstNonBlank(tx.values.get("propagation"), tx.values.get("value"));
        if (prop != null && !prop.isBlank()) {
            tags.put(TAG_TX_PROPAGATION, normalizeEnumLike(prop));
        }
        // readOnly
        String ro = tx.values.get("readOnly");
        if (ro != null && !ro.isBlank()) tags.put(TAG_TX_READ_ONLY, ro.trim());
        // isolation
        String iso = tx.values.get("isolation");
        if (iso != null && !iso.isBlank()) tags.put(TAG_TX_ISOLATION, normalizeEnumLike(iso));

        return tags;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /**
     * Normalize enum-like attribute strings such as:
     * - "REQUIRED"
     * - "TxType.REQUIRED"
     * - "Propagation.REQUIRES_NEW"
     */
    private static String normalizeEnumLike(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        // remove braces/brackets common in array representations
        s = s.replace("[", "").replace("]", "").trim();
        // If multiple values, keep comma-separated normalized tokens
        if (s.contains(",")) {
            String[] parts = s.split(",");
            List<String> norm = new ArrayList<>();
            for (String p : parts) {
                String n = normalizeEnumLike(p);
                if (!n.isBlank()) norm.add(n);
            }
            return String.join(",", norm);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < s.length()) s = s.substring(dot + 1);
        return s.toUpperCase(Locale.ROOT);
    }

    private static boolean hasAnnotation(List<JAnnotationUse> anns, String simple, String... qualifiedNames) {
        if (anns == null || anns.isEmpty()) return false;
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            if (simple != null && simple.equals(a.simpleName)) return true;
            if (a.qualifiedName == null) continue;
            for (String qn : qualifiedNames) {
                if (qn == null) continue;
                if (qn.equals(a.qualifiedName)) return true;
            }
        }
        return false;
    }

    // Must match key format used by UmlFeatureBuilder registration (and RestEndpointExtractor)
    private static String methodKey(JType owner, JMethod m) {
        String qn = owner == null ? "" : (owner.qualifiedName == null ? "" : owner.qualifiedName);
        String name = m == null ? "" : (m.name == null ? "" : m.name);
        StringBuilder sb = new StringBuilder();
        sb.append(qn).append("#").append(name).append("(");
        if (m != null && m.params != null && !m.params.isEmpty()) {
            for (int i = 0; i < m.params.size(); i++) {
                JParam p = m.params.get(i);
                if (i > 0) sb.append(",");
                String pt = p == null ? "" : (p.type == null ? "" : p.type);
                sb.append(pt);
            }
        }
        sb.append(")");
        return sb.toString();
    }
}

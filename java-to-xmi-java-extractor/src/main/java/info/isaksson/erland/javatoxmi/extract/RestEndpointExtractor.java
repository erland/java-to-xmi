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
 * Best-effort extraction of REST endpoints for JAX-RS and Spring MVC.
 *
 * <p>Outputs {@link JRuntimeAnnotation} entries that the UML builder can annotate onto
 * existing classifiers/operations.</p>
 */
final class RestEndpointExtractor {

    // Runtime tag keys (shared convention with IrRuntime).
    static final String TAG_PREFIX = "runtime.";
    static final String TAG_PATH = TAG_PREFIX + "path";
    static final String TAG_HTTP_METHOD = TAG_PREFIX + "httpMethod";
    static final String TAG_CONSUMES = TAG_PREFIX + "consumes";
    static final String TAG_PRODUCES = TAG_PREFIX + "produces";

    // Runtime stereotypes (shared convention with IrRuntime).
    static final String ST_REST_RESOURCE = "RestResource";
    static final String ST_REST_OPERATION = "RestOperation";

    // JAX-RS
    private static final String JAXRS_PATH_QN_1 = "javax.ws.rs.Path";
    private static final String JAXRS_PATH_QN_2 = "jakarta.ws.rs.Path";

    // Spring
    private static final String SPRING_REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SPRING_CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String SPRING_REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String SPRING_GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String SPRING_POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String SPRING_PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private static final String SPRING_DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    private static final String SPRING_PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";

    void extract(JModel model) {
        if (model == null || model.types == null || model.types.isEmpty()) return;

        List<JRuntimeAnnotation> out = new ArrayList<>();
        for (JType t : model.types) {
            if (t == null) continue;

            RestClassInfo rc = detectRestClass(t);
            if (!rc.isRest) continue;

            // Class stereotype
            Map<String, String> classTags = new LinkedHashMap<>();
            if (!rc.basePath.isBlank()) classTags.put(TAG_PATH, rc.basePath);
            out.add(new JRuntimeAnnotation(t.qualifiedName, ST_REST_RESOURCE, classTags));

            // Methods
            for (JMethod m : t.methods) {
                if (m == null) continue;
                RestMethodInfo rm = detectRestMethod(m);
                if (!rm.isRest) continue;

                String fullPath = normalizePath(joinPaths(rc.basePath, rm.path));

                Map<String, String> tags = new LinkedHashMap<>();
                if (!fullPath.isBlank()) tags.put(TAG_PATH, fullPath);
                if (!rm.httpMethods.isBlank()) tags.put(TAG_HTTP_METHOD, rm.httpMethods);
                if (!rm.consumes.isBlank()) tags.put(TAG_CONSUMES, rm.consumes);
                if (!rm.produces.isBlank()) tags.put(TAG_PRODUCES, rm.produces);

                out.add(new JRuntimeAnnotation(methodKey(t, m), ST_REST_OPERATION, tags));
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

    private static RestClassInfo detectRestClass(JType t) {
        boolean isJaxrs = hasAnnotation(t.annotations, "Path", JAXRS_PATH_QN_1, JAXRS_PATH_QN_2);
        boolean isSpring = hasAnnotation(t.annotations, "RestController", SPRING_REST_CONTROLLER)
                || hasAnnotation(t.annotations, "Controller", SPRING_CONTROLLER)
                || hasAnnotation(t.annotations, "RequestMapping", SPRING_REQUEST_MAPPING);

        String basePath = "";
        if (isJaxrs) {
            basePath = firstAnnotationValue(t.annotations, "Path", JAXRS_PATH_QN_1, JAXRS_PATH_QN_2);
        } else if (isSpring) {
            basePath = firstMappingPath(t.annotations);
        }
        return new RestClassInfo(isJaxrs || isSpring, normalizePath(basePath));
    }

    private static RestMethodInfo detectRestMethod(JMethod m) {
        // JAX-RS verbs
        String verb = jaxrsVerb(m.annotations);
        if (!verb.isBlank()) {
            String p = firstAnnotationValue(m.annotations, "Path", JAXRS_PATH_QN_1, JAXRS_PATH_QN_2);
            String produces = firstAnnotationValue(m.annotations, "Produces", "javax.ws.rs.Produces", "jakarta.ws.rs.Produces");
            String consumes = firstAnnotationValue(m.annotations, "Consumes", "javax.ws.rs.Consumes", "jakarta.ws.rs.Consumes");
            return new RestMethodInfo(true, normalizePath(p), normalizeHttpMethods(verb), normalizeMediaList(consumes), normalizeMediaList(produces));
        }

        // Spring mappings
        SpringMapping sm = springMapping(m.annotations);
        if (sm != null && sm.isRest) {
            return new RestMethodInfo(true,
                    normalizePath(sm.path),
                    normalizeHttpMethods(sm.httpMethods),
                    normalizeMediaList(sm.consumes),
                    normalizeMediaList(sm.produces));
        }
        return new RestMethodInfo(false, "", "", "", "");
    }

    private static String jaxrsVerb(List<JAnnotationUse> anns) {
        if (anns == null) return "";
        // common verbs
        if (hasAnnotation(anns, "GET", "javax.ws.rs.GET", "jakarta.ws.rs.GET")) return "GET";
        if (hasAnnotation(anns, "POST", "javax.ws.rs.POST", "jakarta.ws.rs.POST")) return "POST";
        if (hasAnnotation(anns, "PUT", "javax.ws.rs.PUT", "jakarta.ws.rs.PUT")) return "PUT";
        if (hasAnnotation(anns, "DELETE", "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE")) return "DELETE";
        if (hasAnnotation(anns, "PATCH", "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH")) return "PATCH";
        if (hasAnnotation(anns, "HEAD", "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD")) return "HEAD";
        if (hasAnnotation(anns, "OPTIONS", "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS")) return "OPTIONS";
        return "";
    }

    private static SpringMapping springMapping(List<JAnnotationUse> anns) {
        if (anns == null) return null;

        // Specialized shortcuts
        if (hasAnnotation(anns, "GetMapping", SPRING_GET_MAPPING)) return SpringMapping.of("GET", firstMappingPath(anns), firstMappingConsumes(anns), firstMappingProduces(anns));
        if (hasAnnotation(anns, "PostMapping", SPRING_POST_MAPPING)) return SpringMapping.of("POST", firstMappingPath(anns), firstMappingConsumes(anns), firstMappingProduces(anns));
        if (hasAnnotation(anns, "PutMapping", SPRING_PUT_MAPPING)) return SpringMapping.of("PUT", firstMappingPath(anns), firstMappingConsumes(anns), firstMappingProduces(anns));
        if (hasAnnotation(anns, "DeleteMapping", SPRING_DELETE_MAPPING)) return SpringMapping.of("DELETE", firstMappingPath(anns), firstMappingConsumes(anns), firstMappingProduces(anns));
        if (hasAnnotation(anns, "PatchMapping", SPRING_PATCH_MAPPING)) return SpringMapping.of("PATCH", firstMappingPath(anns), firstMappingConsumes(anns), firstMappingProduces(anns));

        // RequestMapping(method=...)
        JAnnotationUse rm = firstAnnotation(anns, "RequestMapping", SPRING_REQUEST_MAPPING);
        if (rm == null) return null;

        String path = mappingPathFrom(rm);
        String consumes = mappingConsumesFrom(rm);
        String produces = mappingProducesFrom(rm);

        String methodAttr = (rm.values == null) ? null : rm.values.get("method");
        String http = "";
        if (methodAttr != null && !methodAttr.isBlank()) {
            // Best-effort: expect something like "GET" or "RequestMethod.GET" or "[GET,POST]".
            http = methodAttr;
        }
        return SpringMapping.of(http, path, consumes, produces);
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

    private static JAnnotationUse firstAnnotation(List<JAnnotationUse> anns, String simple, String... qualifiedNames) {
        if (anns == null) return null;
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            if (simple != null && simple.equals(a.simpleName)) return a;
            if (a.qualifiedName == null) continue;
            for (String qn : qualifiedNames) {
                if (qn == null) continue;
                if (qn.equals(a.qualifiedName)) return a;
            }
        }
        return null;
    }

    private static String firstAnnotationValue(List<JAnnotationUse> anns, String simple, String... qualifiedNames) {
        JAnnotationUse a = firstAnnotation(anns, simple, qualifiedNames);
        if (a == null || a.values == null || a.values.isEmpty()) return "";
        String v = a.values.get("value");
        if (v == null) v = a.values.get("path");
        return v == null ? "" : v;
    }

    private static String firstMappingPath(List<JAnnotationUse> anns) {
        if (anns == null) return "";
        // Prefer mapping annotations in order.
        for (String sn : new String[]{"RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"}) {
            for (JAnnotationUse a : anns) {
                if (a == null) continue;
                if (!sn.equals(a.simpleName)) continue;
                String p = mappingPathFrom(a);
                if (p != null && !p.isBlank()) return p;
            }
        }
        return "";
    }

    private static String firstMappingConsumes(List<JAnnotationUse> anns) {
        if (anns == null) return "";
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            if (a.simpleName == null) continue;
            if (a.simpleName.endsWith("Mapping")) {
                String c = mappingConsumesFrom(a);
                if (c != null && !c.isBlank()) return c;
            }
        }
        return "";
    }

    private static String firstMappingProduces(List<JAnnotationUse> anns) {
        if (anns == null) return "";
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            if (a.simpleName == null) continue;
            if (a.simpleName.endsWith("Mapping")) {
                String p = mappingProducesFrom(a);
                if (p != null && !p.isBlank()) return p;
            }
        }
        return "";
    }

    private static String mappingPathFrom(JAnnotationUse a) {
        if (a == null || a.values == null) return "";
        String v = a.values.get("path");
        if (v == null || v.isBlank()) v = a.values.get("value");
        return v == null ? "" : v;
    }

    private static String mappingConsumesFrom(JAnnotationUse a) {
        if (a == null || a.values == null) return "";
        String v = a.values.get("consumes");
        return v == null ? "" : v;
    }

    private static String mappingProducesFrom(JAnnotationUse a) {
        if (a == null || a.values == null) return "";
        String v = a.values.get("produces");
        return v == null ? "" : v;
    }

    private static String joinPaths(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        if (b.isEmpty()) return s;
        if (s.isEmpty()) return b;
        if (b.endsWith("/") && s.startsWith("/")) return b + s.substring(1);
        if (!b.endsWith("/") && !s.startsWith("/")) return b + "/" + s;
        return b + s;
    }

    private static String normalizePath(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        // strip quotes that can appear in extracted literal text
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("/")) s = "/" + s;
        // collapse multiple slashes
        while (s.contains("//")) s = s.replace("//", "/");
        // remove trailing slash (except root)
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String normalizeHttpMethods(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        s = s.replace("RequestMethod.", "");
        s = s.replace("[", "").replace("]", "");
        s = s.replace("{", "").replace("}", "");
        s = s.replace("\"", "").replace("'", "");
        // Normalize separators
        s = s.replace(";", ",");
        s = s.replace(" ", ",");
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim();
            if (v.isEmpty()) continue;
            out.add(v.toUpperCase(Locale.ROOT));
        }
        return String.join(",", out);
    }

    private static String normalizeMediaList(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        s = s.replace("[", "").replace("]", "");
        s = s.replace("{", "").replace("}", "");
        s = s.replace("\"", "").replace("'", "");
        // allow comma/space separated
        s = s.replace(";", ",");
        while (s.contains("  ")) s = s.replace("  ", " ");
        s = s.replace(" ", ",");
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim();
            if (v.isEmpty()) continue;
            out.add(v);
        }
        return String.join(",", out);
    }

    private static String methodKey(JType t, JMethod m) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.qualifiedName).append("#");
        sb.append(m.name).append("(");
        boolean first = true;
        for (JParam p : m.params) {
            if (!first) sb.append(',');
            first = false;
            sb.append(p.type);
        }
        sb.append(")");
        return sb.toString();
    }

    private record RestClassInfo(boolean isRest, String basePath) {
    }

    private record RestMethodInfo(boolean isRest, String path, String httpMethods, String consumes, String produces) {
    }

    private static final class SpringMapping {
        final boolean isRest;
        final String httpMethods;
        final String path;
        final String consumes;
        final String produces;

        private SpringMapping(boolean isRest, String httpMethods, String path, String consumes, String produces) {
            this.isRest = isRest;
            this.httpMethods = httpMethods == null ? "" : httpMethods;
            this.path = path == null ? "" : path;
            this.consumes = consumes == null ? "" : consumes;
            this.produces = produces == null ? "" : produces;
        }

        static SpringMapping of(String httpMethods, String path, String consumes, String produces) {
            boolean isRest = (httpMethods != null && !httpMethods.isBlank())
                    || (path != null && !path.isBlank());
            return new SpringMapping(isRest, httpMethods == null ? "" : httpMethods, path, consumes, produces);
        }
    }
}

package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Step 3: Parse Java source files into a compact internal semantic model.
 *
 * <p>This class is intentionally kept as a small orchestrator that delegates to
 * specialized helpers in this package:</p>
 * <ul>
 *   <li>{@link JavaCompilationUnitParser}: parse Java source files into compilation units</li>
 *   <li>{@link ProjectTypeIndexBuilder}: build a project type index (qualified name -> stub) including nested member types</li>
 *   <li>{@link TypeExtractionEngine}: extract {@code JType}/{@code JField}/{@code JMethod} into {@link JModel}</li>
 * </ul>
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
     * @param includeDependencies when true, extracts additional conservative dependencies
     *                            from method/constructor bodies (approximate call graph). These are only
     *                            emitted to XMI if dependency output is enabled.
     */
    public JModel extract(Path sourceRoot, List<Path> javaFiles, boolean includeDependencies) {
        JModel model = new JModel(sourceRoot, javaFiles);

        // 1) Parse all compilation units (collect parse errors but continue)
        List<ParsedUnit> units = JavaCompilationUnitParser.parseAll(parser, sourceRoot, javaFiles, model);

        // 2) Build project type index (qualified name -> stub), including nested member types.
        ProjectTypeIndex index = ProjectTypeIndexBuilder.build(units);

        // 3) Extract types (re-walk per compilation unit to keep import context correct for each file)
        TypeExtractionEngine.extractAllTypes(model, units, index, includeDependencies);

        // 4) Extract runtime semantics (REST endpoints etc.)
        new RestEndpointExtractor().extract(model);

        // 5) Extract CDI runtime semantics (events + observers)
        new CdiEventExtractor().extract(model, units, index);

        // 6) Extract interceptor/transaction boundaries
        new InterceptorAndTransactionExtractor().extract(model);

        // 7) Extract messaging + scheduled jobs
        new MessagingAndSchedulingExtractor().extract(model);

        // 8) Extract Flyway migration artifacts
        new FlywayMigrationExtractor().extract(model, units);

        // 9) Extract JPMS module boundaries (module-info.java)
        new JpmsModuleExtractor().extract(model, units);


        // Stable ordering for downstream determinism
        model.types.sort(Comparator.comparing(t -> t.qualifiedName));
        return model;
    }

    /**
     * Propagate JPA relationship annotations from getter methods onto the corresponding backing fields.
     *
     * <p>This supports common "property access" style in JPA where annotations are placed on
     * {@code getX()} rather than on the field {@code x}. This is best-effort and intentionally
     * conservative: only JPA relationship-related annotations are propagated.</p>
     */
    static void propagateJpaRelationshipAnnotationsFromGettersToFields(
            List<info.isaksson.erland.javatoxmi.model.JField> fields,
            List<info.isaksson.erland.javatoxmi.model.JMethod> methods
    ) {
        if (fields == null || fields.isEmpty() || methods == null || methods.isEmpty()) return;

        // Field name -> index for replacement.
        Map<String, Integer> fieldIndex = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            info.isaksson.erland.javatoxmi.model.JField f = fields.get(i);
            if (f == null) continue;
            String n = f.name == null ? "" : f.name.trim();
            if (!n.isEmpty() && !fieldIndex.containsKey(n)) fieldIndex.put(n, i);
        }

        for (info.isaksson.erland.javatoxmi.model.JMethod m : methods) {
            if (m == null) continue;
            if (m.params != null && !m.params.isEmpty()) continue; // must be a zero-arg getter
            if (m.isConstructor) continue;

            String prop = getterPropertyName(m);
            if (prop == null) continue;

            if (m.annotations == null || m.annotations.isEmpty()) continue;
            List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> relAnns = m.annotations.stream()
                    .filter(JavaExtractor::isJpaRelationshipOrJoinAnnotation)
                    .collect(Collectors.toList());
            if (relAnns.isEmpty()) continue;

            Integer idx = fieldIndex.get(prop);
            if (idx == null) continue;
            info.isaksson.erland.javatoxmi.model.JField f = fields.get(idx);
            if (f == null) continue;

            List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> merged = mergeAnnotations(f.annotations, relAnns);
            if (merged == f.annotations || merged.equals(f.annotations)) continue;

            fields.set(idx, new info.isaksson.erland.javatoxmi.model.JField(f.name, f.type, f.typeRef, f.visibility, f.isStatic, f.isFinal, merged));
        }
    }

    private static String getterPropertyName(info.isaksson.erland.javatoxmi.model.JMethod m) {
        String n = m.name == null ? "" : m.name.trim();
        if (n.isEmpty()) return null;

        // getX(): any return type (non-empty) in our model
        if (n.startsWith("get") && n.length() > 3) {
            if (m.returnType == null || m.returnType.isBlank()) return null;
            return decapitalize(n.substring(3));
        }
        // isX(): typical boolean getter
        if (n.startsWith("is") && n.length() > 2) {
            String rt = m.returnType == null ? "" : m.returnType.trim();
            if (!(rt.equals("boolean") || rt.equals("java.lang.Boolean") || rt.equals("Boolean"))) {
                // Be conservative: only treat as getter for boolean-ish returns.
                return null;
            }
            return decapitalize(n.substring(2));
        }
        return null;
    }

    private static String decapitalize(String s) {
        if (s == null) return null;
        if (s.isEmpty()) return null;
        if (s.length() == 1) return s.toLowerCase(Locale.ROOT);
        // Handle JavaBeans style: if first two are uppercase, keep as-is ("URL" -> "URL").
        if (Character.isUpperCase(s.charAt(0)) && Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isJpaRelationshipOrJoinAnnotation(info.isaksson.erland.javatoxmi.model.JAnnotationUse a) {
        if (a == null) return false;
        String sn = a.simpleName == null ? "" : a.simpleName.trim();
        String qn = a.qualifiedName == null ? "" : a.qualifiedName.trim();

        // Relationship annotations
        if (isAny(sn, qn,
                "OneToOne", "OneToMany", "ManyToOne", "ManyToMany",
                "Embedded", "ElementCollection")) return true;

        // Common join/config annotations frequently placed alongside relationships
        return isAny(sn, qn,
                "JoinColumn", "JoinColumns", "JoinTable", "MapsId",
                "OrderBy", "OrderColumn", "PrimaryKeyJoinColumn", "PrimaryKeyJoinColumns");
    }

    private static boolean isAny(String simpleName, String qualifiedName, String... names) {
        if (names == null || names.length == 0) return false;
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            if (n.equals(simpleName)) return true;
            if (qualifiedName.endsWith("." + n)) return true;
            // also accept exact qualified match
            if (qualifiedName.equals(n)) return true;
        }
        return false;
    }

    private static List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> mergeAnnotations(
            List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> base,
            List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> extra
    ) {
        if (extra == null || extra.isEmpty()) return base == null ? List.of() : base;
        List<info.isaksson.erland.javatoxmi.model.JAnnotationUse> out = new ArrayList<>();
        if (base != null) out.addAll(base);

        // Track existing by qualifiedName when present, else by simpleName.
        Set<String> seen = new HashSet<>();
        for (info.isaksson.erland.javatoxmi.model.JAnnotationUse a : out) {
            if (a == null) continue;
            String key = (a.qualifiedName != null && !a.qualifiedName.isBlank()) ? a.qualifiedName : a.simpleName;
            if (key != null && !key.isBlank()) seen.add(key);
        }
        for (info.isaksson.erland.javatoxmi.model.JAnnotationUse a : extra) {
            if (a == null) continue;
            String key = (a.qualifiedName != null && !a.qualifiedName.isBlank()) ? a.qualifiedName : a.simpleName;
            if (key == null || key.isBlank()) continue;
            if (seen.contains(key)) continue;
            out.add(a);
            seen.add(key);
        }
        return List.copyOf(out);
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
}

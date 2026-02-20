package se.erland.javatoxmi.report;

import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.UnresolvedTypeRef;
import se.erland.javatoxmi.uml.UmlBuildStats;

import org.eclipse.uml2.uml.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Human-readable markdown report (Step 6).
 *
 * NOTE: "External type refs" are types we inferred as external (e.g., java.lang.String, java.util.List).
 * "Unresolved (unknown)" are types we could not qualify at all (likely missing deps or typos).
 */
public final class ReportGenerator {

    private ReportGenerator() {}

    public static void writeMarkdown(Path reportPath,
                                     Path sourcePath,
                                     Path xmiPath,
                                     JModel jModel,
                                     Model umlModel,
                                     UmlBuildStats umlStats,
                                     List<Path> discoveredJavaFiles,
                                     boolean includeTests,
                                     List<String> excludes,
                                     boolean failOnUnresolved) throws IOException {

        StringBuilder report = new StringBuilder();
        report.append("# java-to-xmi report\n\n");

        report.append("## Summary\n\n");
        report.append("- Source: `").append(sourcePath).append("`\n");
        report.append("- XMI: `").append(xmiPath).append("`\n");
        report.append("- Java files discovered: **").append(discoveredJavaFiles.size()).append("**\n");
        report.append("- Types extracted: **").append(jModel.types.size()).append("**\n");
        report.append("- Parse errors: **").append(jModel.parseErrors.size()).append("**\n");
        report.append("- External type refs (stubbed): **").append(jModel.externalTypeRefs.size()).append("**\n");
        report.append("- Unresolved type refs (unknown): **").append(jModel.unresolvedTypes.size()).append("**\n");
        report.append("- Annotation uses: **").append(countAnnotationUses(jModel)).append("**\n");
        report.append("- Annotated types: **").append(countAnnotatedTypes(jModel)).append("**\n");
        report.append("- Include tests: **").append(includeTests).append("**\n");
        report.append("- Fail on unresolved: **").append(failOnUnresolved).append("**\n");
        report.append("- Excludes: ").append(excludes.isEmpty() ? "_(none)_" : "`" + String.join("`, `", excludes) + "`").append("\n\n");

        report.append("## UML build stats\n\n");
        report.append("- Model name: `").append(umlModel.getName()).append("`\n");
        report.append("- Packages: **").append(umlStats.packagesCreated).append("**\n");
        report.append("- Package imports: **").append(umlStats.packageImportsCreated).append("**\n");
        report.append("- Classifiers: **").append(umlStats.classifiersCreated).append("**\n");
        report.append("- Attributes: **").append(umlStats.attributesCreated).append("**\n");
        report.append("- Operations: **").append(umlStats.operationsCreated).append("**\n");
        report.append("- Parameters: **").append(umlStats.parametersCreated).append("**\n");
        report.append("- Generalizations: **").append(umlStats.generalizationsCreated).append("**\n");
        report.append("- Interface realizations: **").append(umlStats.interfaceRealizationsCreated).append("**\n");
        report.append("- Associations: **").append(umlStats.associationsCreated).append("**\n");
        if (umlStats.associationMerges > 0) {
            report.append("  - merged bidirectional JPA associations: **").append(umlStats.associationMerges).append("**\n");
        }
        report.append("- Dependencies: **").append(umlStats.dependenciesCreated).append("**\n");
        report.append("- External stubs: **").append(umlStats.externalStubsCreated).append("**\n\n");

        report.append("## Discovered files\n");
        for (Path p : discoveredJavaFiles) {
            report.append("- `").append(sourcePath.relativize(p).toString().replace("\\", "/")).append("`\n");
        }

        report.append("\n## Extracted types\n\n");
        List<JType> sortedTypes = new ArrayList<>(jModel.types);
        sortedTypes.sort(Comparator.comparing(t -> t.qualifiedName));
        for (JType t : sortedTypes) {
            report.append("- `").append(t.qualifiedName).append("` (").append(t.kind).append(")");
            if (t.extendsType != null && !t.extendsType.isBlank()) {
                report.append(" extends `").append(t.extendsType).append("`");
            }
            if (!t.implementsTypes.isEmpty()) {
                report.append(" implements ");
                report.append(t.implementsTypes.stream().map(x -> "`" + x + "`").reduce((a,b) -> a + ", " + b).orElse(""));
            }
            report.append("\n");
        }

        report.append("\n## Annotations (stereotypes)\n\n");
        Map<String, AnnotationSummary> ann = summarizeAnnotations(jModel);
        if (ann.isEmpty()) {
            report.append("_(none)_\n");
        } else {
            report.append("| Annotation | Uses | Keys |\n");
            report.append("|---|---:|---|\n");
            for (var e : ann.entrySet()) {
                AnnotationSummary s = e.getValue();
                report.append("| `").append(e.getKey()).append("` | ")
                        .append(s.uses).append(" | ")
                        .append(s.keys.isEmpty() ? "_(none)_" : s.keys).append(" |\n");
            }
            report.append("\n");
            report.append("Notes:\n");
            report.append("- Each annotation type is represented as a UML Stereotype in the `JavaAnnotations` Profile.\n");
            report.append("- Tag keys are derived from annotation member/value pairs.\n");
        }

        report.append("\n## Parse errors\n\n");
        if (jModel.parseErrors.isEmpty()) {
            report.append("_(none)_\n");
        } else {
            for (String pe : jModel.parseErrors) {
                report.append("- ").append(pe).append("\n");
            }
        }

        report.append("\n## External type references (stubbed)\n\n");
        if (jModel.externalTypeRefs.isEmpty()) {
            report.append("_(none)_\n");
        } else {
            List<UnresolvedTypeRef> ur = new ArrayList<>(jModel.externalTypeRefs);
            ur.sort(Comparator.comparing(u -> u.referencedType + "|" + u.fromQualifiedType + "|" + u.where));
            report.append("Top external types:\n\n");
            writeGroupedTypeRefs(report, ur);
            report.append("\nAll external refs:\n\n");
            for (UnresolvedTypeRef u : ur) {
                report.append("- ").append(u.toString()).append("\n");
            }
        }

        report.append("\n## Unresolved type references (unknown)\n\n");
        if (jModel.unresolvedTypes.isEmpty()) {
            report.append("_(none)_\n");
        } else {
            List<UnresolvedTypeRef> ur2 = new ArrayList<>(jModel.unresolvedTypes);
            ur2.sort(Comparator.comparing(u -> u.referencedType + "|" + u.fromQualifiedType + "|" + u.where));
            report.append("Top unresolved types:\n\n");
            writeGroupedTypeRefs(report, ur2);
            report.append("\nAll unresolved refs:\n\n");
            for (UnresolvedTypeRef u : ur2) {
                report.append("- ").append(u.toString()).append("\n");
            }
        }

        report.append("\n## Notes\n\n");
        report.append("- External type refs are inferred via imports/wildcards/java.lang. They are created as lightweight stubs in UML for typing.\n");
        report.append("- Unknown unresolved refs are types we could not qualify at all (missing deps/typos).\n");
        report.append("- Deterministic XMI export is achieved by assigning stable `xmi:id` values before serialization.\n");
        report.append("- Annotation applications are emitted as an XMI `xmi:Extension` block (tool-friendly and avoids UML2 profile application APIs).\n");

        Files.createDirectories(reportPath.toAbsolutePath().normalize().getParent());
        Files.writeString(reportPath, report.toString());
    }

    private static int countAnnotatedTypes(JModel m) {
        int n = 0;
        for (JType t : m.types) {
            if (t.annotations != null && !t.annotations.isEmpty()) n++;
        }
        return n;
    }

    private static int countAnnotationUses(JModel m) {
        int n = 0;
        for (JType t : m.types) {
            if (t.annotations != null) n += t.annotations.size();
        }
        return n;
    }

    private static Map<String, AnnotationSummary> summarizeAnnotations(JModel m) {
        // LinkedHashMap preserves insertion order; we insert keys in sorted order for stable output.
        Map<String, AnnotationSummary> tmp = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        for (JType t : m.types) {
            if (t.annotations == null) continue;
            for (var a : t.annotations) {
                String qn = a.qualifiedName != null && !a.qualifiedName.isBlank() ? a.qualifiedName : a.simpleName;
                if (qn == null) continue;
                if (!tmp.containsKey(qn)) keys.add(qn);
            }
        }
        keys.sort(String::compareTo);
        for (String k : keys) tmp.put(k, new AnnotationSummary());

        for (JType t : m.types) {
            if (t.annotations == null) continue;
            for (var a : t.annotations) {
                String qn = a.qualifiedName != null && !a.qualifiedName.isBlank() ? a.qualifiedName : a.simpleName;
                if (qn == null) continue;
                AnnotationSummary s = tmp.get(qn);
                if (s == null) continue;
                s.uses++;
                if (a.values != null) {
                    List<String> vKeys = new ArrayList<>(a.values.keySet());
                    vKeys.sort(String::compareTo);
                    for (String vk : vKeys) {
                        if (vk == null || vk.isBlank()) continue;
                        s.keySet.put(vk, Boolean.TRUE);
                    }
                }
            }
        }

        // finalize keys column
        for (var e : tmp.entrySet()) {
            AnnotationSummary s = e.getValue();
            s.keys = String.join(", ", s.keySet.keySet());
        }
        return tmp;
    }

    private static void writeGroupedTypeRefs(StringBuilder report, List<UnresolvedTypeRef> refs) {
        // Group by referencedType with counts (stable order: by referencedType)
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (UnresolvedTypeRef r : refs) {
            String k = r.referencedType;
            counts.put(k, counts.getOrDefault(k, 0) + 1);
        }
        // refs already sorted by caller; counts will follow first-seen order which is referencedType order.
        report.append("| Type | Count |\n");
        report.append("|---|---:|\n");
        for (var e : counts.entrySet()) {
            report.append("| `").append(e.getKey()).append("` | ").append(e.getValue()).append(" |\n");
        }
    }

    private static final class AnnotationSummary {
        int uses = 0;
        // value doesn't matter; use LinkedHashMap for stable insertion ordering
        final Map<String, Boolean> keySet = new LinkedHashMap<>();
        String keys = "";
    }
}

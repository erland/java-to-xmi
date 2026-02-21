package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Local resolution helper: maps a type reference (qualified or simple) to a classifier in the current build.
 */
final class LocalClassifierResolver {

    Classifier resolveLocalClassifier(UmlBuildContext ctx, String possiblySimpleOrQualified) {
        if (ctx == null) return null;
        if (possiblySimpleOrQualified == null || possiblySimpleOrQualified.isBlank()) return null;
        Classifier direct = ctx.classifierByQName.get(possiblySimpleOrQualified);
        if (direct != null) return direct;

        // If it's a simple name, try to find a unique qualified match.
        String name = possiblySimpleOrQualified;
        if (!name.contains(".")) {
            List<String> matches = new ArrayList<>();
            for (String qn : ctx.classifierByQName.keySet()) {
                if (qn.equals(name) || qn.endsWith("." + name)) {
                    matches.add(qn);
                }
            }
            matches.sort(String::compareTo);
            if (!matches.isEmpty()) {
                return ctx.classifierByQName.get(matches.get(0));
            }
        }
        return null;
    }
}

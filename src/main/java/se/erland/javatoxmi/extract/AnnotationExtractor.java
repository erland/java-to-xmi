package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import se.erland.javatoxmi.model.JAnnotationUse;

import java.util.*;

/**
 * Best-effort annotation extraction and value normalisation.
 *
 * <p>Kept deliberately tolerant: annotation parsing should never break extraction.</p>
 */
final class AnnotationExtractor {

    private AnnotationExtractor() {}

    static List<JAnnotationUse> extract(NodeWithAnnotations<?> node, ImportContext ctx) {
        if (node == null) return List.of();
        try {
            NodeList<AnnotationExpr> anns = node.getAnnotations();
            if (anns == null || anns.isEmpty()) return List.of();
            List<JAnnotationUse> out = new ArrayList<>();
            for (AnnotationExpr ae : anns) {
                if (ae == null) continue;
                String rawName = ae.getNameAsString();
                String simple = rawName;
                String qualified = null;

                if (rawName != null && rawName.contains(".")) {
                    int dot = rawName.lastIndexOf('.');
                    simple = rawName.substring(dot + 1);
                    qualified = rawName;
                } else if (rawName != null && !rawName.isBlank()) {
                    qualified = ctx.qualifyAnnotation(rawName);
                }

                Map<String, String> values = new LinkedHashMap<>();
                if (ae instanceof NormalAnnotationExpr) {
                    NormalAnnotationExpr nae = (NormalAnnotationExpr) ae;
                    for (MemberValuePair p : nae.getPairs()) {
                        if (p == null) continue;
                        String k = p.getNameAsString();
                        String v = normalizeValue(p.getValue());
                        if (k != null && !k.isBlank()) values.put(k, v);
                    }
                } else if (ae instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr sae = (SingleMemberAnnotationExpr) ae;
                    values.put("value", normalizeValue(sae.getMemberValue()));
                } else {
                    // MarkerAnnotationExpr -> no values
                }

                out.add(new JAnnotationUse(simple, qualified, values));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    static String normalizeValue(Expression expr) {
        if (expr == null) return "";
        try {
            if (expr.isStringLiteralExpr()) return expr.asStringLiteralExpr().asString();
            if (expr.isCharLiteralExpr()) return Character.toString(expr.asCharLiteralExpr().asChar());
            if (expr.isBooleanLiteralExpr()) return Boolean.toString(expr.asBooleanLiteralExpr().getValue());
            if (expr.isIntegerLiteralExpr()) return expr.asIntegerLiteralExpr().getValue();
            if (expr.isLongLiteralExpr()) return expr.asLongLiteralExpr().getValue();
            if (expr.isDoubleLiteralExpr()) return expr.asDoubleLiteralExpr().getValue();
            if (expr.isNullLiteralExpr()) return "null";
            if (expr.isClassExpr()) return expr.asClassExpr().getType().toString() + ".class";
            if (expr.isNameExpr()) return expr.asNameExpr().getNameAsString();
            if (expr.isFieldAccessExpr()) return expr.asFieldAccessExpr().toString();
            return expr.toString();
        } catch (Exception e) {
            return expr.toString();
        }
    }
}

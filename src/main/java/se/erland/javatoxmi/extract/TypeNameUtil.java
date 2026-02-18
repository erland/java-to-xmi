package se.erland.javatoxmi.extract;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Small helpers for dealing with Java type strings without symbol solving.
 */
final class TypeNameUtil {

    private TypeNameUtil() {}

    static boolean isNonReferenceType(String typeName) {
        if (typeName == null) return true;
        String t = typeName.trim();
        return t.isBlank()
                || t.equals("void")
                || t.equals("var")
                || t.equals("boolean")
                || t.equals("byte")
                || t.equals("short")
                || t.equals("int")
                || t.equals("long")
                || t.equals("float")
                || t.equals("double")
                || t.equals("char");
    }

    static String primaryBaseName(String rendered) {
        if (rendered == null) return null;
        String r = rendered.trim();
        if (r.isBlank()) return null;
        // strip array suffixes
        while (r.endsWith("[]")) r = r.substring(0, r.length() - 2).trim();
        // strip generics
        int lt = r.indexOf('<');
        if (lt > 0) r = r.substring(0, lt).trim();
        if (r.isBlank()) return null;
        return r;
    }

    static Set<String> extractBaseTypeNames(String rendered) {
        Set<String> out = new LinkedHashSet<>();
        if (rendered == null) return out;
        String s = rendered;
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isJavaIdentifierPart(c) || c == '.';
            if (ok) {
                token.append(c);
            } else {
                flushToken(out, token);
            }
        }
        flushToken(out, token);
        return out;
    }

    private static void flushToken(Set<String> out, StringBuilder token) {
        if (token.length() == 0) return;
        String t = token.toString();
        token.setLength(0);
        if (t.isBlank()) return;
        // filter obvious keywords
        if (t.equals("extends") || t.equals("super")) return;
        out.add(t);
    }

    static String replacePrimaryBaseName(String rendered, String primary, String replacement) {
        if (rendered == null || primary == null || replacement == null) return rendered;
        // Replace only the first occurrence of the primary base name at an identifier boundary.
        // This is best-effort and matches the original extractor intent.
        int idx = rendered.indexOf(primary);
        if (idx < 0) return rendered;
        return rendered.substring(0, idx) + replacement + rendered.substring(idx + primary.length());
    }
}

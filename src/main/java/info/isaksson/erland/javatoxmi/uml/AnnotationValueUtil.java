package info.isaksson.erland.javatoxmi.uml;

/** Shared utilities for interpreting annotation names/values. */
final class AnnotationValueUtil {
    private AnnotationValueUtil() {}

    static String stripPkg(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    static Integer parseInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        // Some extractors may store quoted numbers
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        if (!t.matches("-?\\d+")) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package info.isaksson.erland.javatoxmi.xmi;

/**
 * Small utilities for post-processing serialized XMI as text.
 *
 * <p>We keep this deliberately dumb (string-based) to avoid pulling in a DOM parser.
 * The injection points are simple and deterministic in the XMI produced by this tool.</p>
 */
final class XmiDomUtil {

    private XmiDomUtil() {}

    static String injectBeforeClosingXmi(String xml, String extension) {
        if (xml == null || extension == null || extension.isEmpty()) return xml;
        int closeIdx = xml.lastIndexOf("</xmi:XMI>");
        if (closeIdx < 0) return xml;
        return xml.substring(0, closeIdx) + extension + xml.substring(closeIdx);
    }

    static String escapeAttr(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}

package info.isaksson.erland.javatoxmi.uml;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic ID strategy for UML elements.
 *
 * We don't force these IDs into XMI yet (that's Step 5), but we compute and attach
 * them as an EAnnotation so the writer can later set real xmi:ids consistently.
 */
public final class UmlIdStrategy {
    private UmlIdStrategy() {}

    /**
     * Create a stable, compact ID from an input key.
     *
     * We use SHA-256 and truncate to 16 bytes (32 hex chars) which is plenty to avoid collisions
     * for typical model sizes while keeping ids readable.
     */
    public static String id(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed in the JRE.
            throw new IllegalStateException(e);
        }
    }
}

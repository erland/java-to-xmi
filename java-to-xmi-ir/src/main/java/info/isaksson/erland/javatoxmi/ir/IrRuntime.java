package info.isaksson.erland.javatoxmi.ir;

import java.util.Locale;

/**
 * Foundation constants and normalization helpers for representing runtime/framework semantics
 * in the IR using stereotypes + tagged values.
 *
 * <p>This is intentionally technology-agnostic at the IR level: it does not depend on
 * JavaParser/UML2/etc. It only standardizes keys and canonical string formats so emitted
 * JSON/XMI stays deterministic.</p>
 */
public final class IrRuntime {

    private IrRuntime() {}

    /** Prefix for all runtime semantic tag keys. */
    public static final String TAG_PREFIX = "runtime.";

    // Backwards/shortcut constants for commonly-used tag keys (used by tests/emitter layer)
    // Prefer IrRuntime.Tags.* for new code.
    public static final String TAG_EVENT_QUALIFIERS = Tags.QUALIFIERS;

    // Backwards/shortcut constants for stereotype names (used by emitter layer)
    public static final String ST_REST_RESOURCE = Stereotypes.REST_RESOURCE;
    public static final String ST_REST_OPERATION = Stereotypes.REST_OPERATION;
    public static final String ST_INTERCEPTOR = Stereotypes.INTERCEPTOR;
    public static final String ST_TRANSACTIONAL = Stereotypes.TRANSACTIONAL;
    public static final String ST_MESSAGE_CONSUMER = Stereotypes.MESSAGE_CONSUMER;
    public static final String ST_MESSAGE_PRODUCER = Stereotypes.MESSAGE_PRODUCER;
    public static final String ST_SCHEDULED_JOB = Stereotypes.SCHEDULED_JOB;
    public static final String ST_FIRES_EVENT = Stereotypes.FIRES_EVENT;
    public static final String ST_OBSERVES_EVENT = Stereotypes.OBSERVES_EVENT;
    public static final String ST_FLYWAY_MIGRATION = Stereotypes.FLYWAY_MIGRATION;
    public static final String ST_JAVA_MODULE = Stereotypes.JAVA_MODULE;


    /**
     * Standard tagged-value keys for runtime semantics.
     *
     * <p>Prefix keys with {@code runtime.} to avoid collisions with user-defined tags.
     * Keep values as simple strings; use comma-separated lists if you need collections.</p>
     */
    public static final class Tags {
        private Tags() {}

        // REST
        public static final String PATH = "runtime.path";
        public static final String HTTP_METHOD = "runtime.httpMethod";
        public static final String CONSUMES = "runtime.consumes";
        public static final String PRODUCES = "runtime.produces";

        // CDI events
        public static final String QUALIFIERS = "runtime.qualifiers";
        public static final String ASYNC = "runtime.async";
        public static final String TX_PHASE = "runtime.transactionPhase";
        public static final String PRIORITY = "runtime.priority";

        // Interceptors / transactions
        public static final String BINDINGS = "runtime.bindings";
        public static final String TRANSACTIONAL = "runtime.transactional";
        public static final String TX_PROPAGATION = "runtime.tx.propagation";
        public static final String TX_READ_ONLY = "runtime.tx.readOnly";
        public static final String TX_ISOLATION = "runtime.tx.isolation";

        // Messaging
        public static final String TOPIC = "runtime.topic";
        public static final String QUEUE = "runtime.queue";
        public static final String GROUP_ID = "runtime.groupId";
        public static final String CONCURRENCY = "runtime.concurrency";

        // Scheduling
        public static final String CRON = "runtime.cron";
        public static final String FIXED_DELAY_MS = "runtime.fixedDelayMs";
        public static final String FIXED_RATE_MS = "runtime.fixedRateMs";

        // Flyway/Liquibase migrations
        public static final String MIGRATION_VERSION = "runtime.migration.version";
        public static final String MIGRATION_DESCRIPTION = "runtime.migration.description";
        public static final String MIGRATION_TYPE = "runtime.migration.type"; // sql/java
        public static final String MIGRATION_PATH = "runtime.migration.path";

        // JPMS
        public static final String MODULE_NAME = "runtime.module.name";
        public static final String MODULE_REQUIRES = "runtime.module.requires"; // comma-separated
        public static final String MODULE_EXPORTS = "runtime.module.exports";   // comma-separated
        public static final String MODULE_OPENS = "runtime.module.opens";       // comma-separated
    }

    /**
     * Standard stereotype names for runtime semantics.
     *
     * <p>These are names only. How they are implemented in UML profiles/XMI is handled
     * in the emitter layer.</p>
     */
    public static final class Stereotypes {
        private Stereotypes() {}

        // REST
        public static final String REST_RESOURCE = "RestResource";
        public static final String REST_OPERATION = "RestOperation";

        // CDI
        public static final String FIRES_EVENT = "FiresEvent";
        public static final String OBSERVES_EVENT = "ObservesEvent";

        // Interceptors / transactions
        public static final String INTERCEPTOR = "Interceptor";
        public static final String TRANSACTIONAL = "Transactional";

        // Messaging / scheduling
        public static final String MESSAGE_CONSUMER = "MessageConsumer";
        public static final String MESSAGE_PRODUCER = "MessageProducer";
        public static final String SCHEDULED_JOB = "ScheduledJob";

        // Migrations / modules
        public static final String FLYWAY_MIGRATION = "FlywayMigration";
        public static final String JAVA_MODULE = "JavaModule";
    }

    // ------------------------
    // Canonicalization helpers
    // ------------------------

    /**
     * Normalize a URL/path-like value to a deterministic representation:
     * <ul>
     *   <li>trim</li>
     *   <li>convert backslashes to slashes</li>
     *   <li>collapse repeated slashes</li>
     *   <li>ensure it starts with a single leading slash (if non-empty)</li>
     *   <li>remove a trailing slash (except for root "/")</li>
     * </ul>
     */
    public static String normalizePath(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('\\', '/');
        if (s.isEmpty()) return "";
        while (s.contains("//")) s = s.replace("//", "/");
        if (!s.startsWith("/")) s = "/" + s;
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Normalize HTTP method names (GET/POST/...) to upper-case. */
    public static String normalizeHttpMethod(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return "";
        return s.toUpperCase(Locale.ROOT);
    }

    /**
     * Normalize message destinations (topics/queues) for deterministic output.
     * This is intentionally conservative: we only trim and collapse whitespace.
     */
    public static String normalizeDestination(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return "";
        return s.replaceAll("\\s+", " ");
    }

    /** Normalize config keys by trimming and collapsing whitespace. */
    public static String normalizeConfigKey(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return "";
        return s.replaceAll("\\s+", "");
    }

    /**
     * Normalize Flyway-like version strings (e.g. "V2" or "2") to a canonical form.
     * This does not attempt semantic comparison; it only normalizes common prefixes.
     */
    public static String normalizeMigrationVersion(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (s.length() >= 2 && (s.charAt(0) == 'V' || s.charAt(0) == 'v')) {
            s = s.substring(1);
        }
        return s;
    }
}

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
 * Best-effort extraction of messaging consumers/producers and scheduled jobs.
 *
 * <p>Outputs {@link JRuntimeAnnotation} entries that the UML builder can annotate onto
 * existing classifiers/operations via {@code java-to-xmi:runtime} and {@code java-to-xmi:tags}.</p>
 *
 * <p>This is intentionally heuristics-based and does not require symbol solving.</p>
 */
final class MessagingAndSchedulingExtractor {

    // Runtime tag keys (shared convention with IrRuntime).
    static final String TAG_PREFIX = "runtime.";
    static final String TAG_DESTINATION = TAG_PREFIX + "destination";
    static final String TAG_DESTINATION_TYPE = TAG_PREFIX + "destinationType"; // topic|queue|channel|pattern
    static final String TAG_GROUP_ID = TAG_PREFIX + "groupId";
    static final String TAG_CONCURRENCY = TAG_PREFIX + "concurrency";
    static final String TAG_ASYNC = TAG_PREFIX + "async";

    static final String TAG_CRON = TAG_PREFIX + "cron";
    static final String TAG_FIXED_DELAY = TAG_PREFIX + "fixedDelay";
    static final String TAG_FIXED_RATE = TAG_PREFIX + "fixedRate";

    // Runtime stereotypes (shared convention with IrRuntime).
    static final String ST_MESSAGE_CONSUMER = "MessageConsumer";
    static final String ST_MESSAGE_PRODUCER = "MessageProducer";
    static final String ST_SCHEDULED_JOB = "ScheduledJob";

    // JMS / EJB
    private static final String MDB_1 = "javax.ejb.MessageDriven";
    private static final String MDB_2 = "jakarta.ejb.MessageDriven";
    private static final String EJB_SCHEDULE_1 = "javax.ejb.Schedule";
    private static final String EJB_SCHEDULE_2 = "jakarta.ejb.Schedule";
    private static final String EJB_TIMEOUT_1 = "javax.ejb.Timeout";
    private static final String EJB_TIMEOUT_2 = "jakarta.ejb.Timeout";

    // Spring
    private static final String SPRING_JMS_LISTENER = "org.springframework.jms.annotation.JmsListener";
    private static final String SPRING_KAFKA_LISTENER = "org.springframework.kafka.annotation.KafkaListener";
    private static final String SPRING_SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";

    // MicroProfile Reactive Messaging
    private static final String MP_INCOMING = "org.eclipse.microprofile.reactive.messaging.Incoming";
    private static final String MP_OUTGOING = "org.eclipse.microprofile.reactive.messaging.Outgoing";

    void extract(JModel model) {
        if (model == null || model.types == null || model.types.isEmpty()) return;

        List<JRuntimeAnnotation> out = new ArrayList<>();

        for (JType t : model.types) {
            if (t == null) continue;

            // Class-level consumers (MessageDriven bean)
            if (hasAnnotation(t.annotations, "MessageDriven", MDB_1, MDB_2)) {
                Map<String, String> tags = new LinkedHashMap<>();
                // Best-effort: common members
                String dest = firstMemberValue(firstAnnotation(t.annotations, "MessageDriven", MDB_1, MDB_2),
                        "destination", "mappedName", "name", "value");
                if (!dest.isBlank()) tags.put(TAG_DESTINATION, dest);
                tags.put(TAG_DESTINATION_TYPE, "queue");
                out.add(new JRuntimeAnnotation(t.qualifiedName, ST_MESSAGE_CONSUMER, tags));
            }

            // Method-level annotations
            for (JMethod m : t.methods) {
                if (m == null) continue;

                // Consumers
                JAnnotationUse kafka = firstAnnotation(m.annotations, "KafkaListener", SPRING_KAFKA_LISTENER);
                if (kafka != null) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    String topics = firstMemberValue(kafka, "topics", "topicPattern", "value");
                    if (!topics.isBlank()) tags.put(TAG_DESTINATION, normalizeCommaList(topics));
                    tags.put(TAG_DESTINATION_TYPE, kafka.values.containsKey("topicPattern") ? "pattern" : "topic");
                    String groupId = firstMemberValue(kafka, "groupId", "group");
                    if (!groupId.isBlank()) tags.put(TAG_GROUP_ID, groupId);
                    String concurrency = firstMemberValue(kafka, "concurrency");
                    if (!concurrency.isBlank()) tags.put(TAG_CONCURRENCY, concurrency);
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_MESSAGE_CONSUMER, tags));
                }

                JAnnotationUse jms = firstAnnotation(m.annotations, "JmsListener", SPRING_JMS_LISTENER);
                if (jms != null) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    String dest = firstMemberValue(jms, "destination", "value");
                    if (!dest.isBlank()) tags.put(TAG_DESTINATION, dest);
                    tags.put(TAG_DESTINATION_TYPE, "queue");
                    String concurrency = firstMemberValue(jms, "concurrency");
                    if (!concurrency.isBlank()) tags.put(TAG_CONCURRENCY, concurrency);
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_MESSAGE_CONSUMER, tags));
                }

                JAnnotationUse incoming = firstAnnotation(m.annotations, "Incoming", MP_INCOMING);
                if (incoming != null) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    String channel = firstMemberValue(incoming, "value");
                    if (!channel.isBlank()) tags.put(TAG_DESTINATION, channel);
                    tags.put(TAG_DESTINATION_TYPE, "channel");
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_MESSAGE_CONSUMER, tags));
                }

                // Producers (annotation-based)
                JAnnotationUse outgoing = firstAnnotation(m.annotations, "Outgoing", MP_OUTGOING);
                if (outgoing != null) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    String channel = firstMemberValue(outgoing, "value");
                    if (!channel.isBlank()) tags.put(TAG_DESTINATION, channel);
                    tags.put(TAG_DESTINATION_TYPE, "channel");
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_MESSAGE_PRODUCER, tags));
                }

                // Scheduled jobs
                JAnnotationUse scheduled = firstAnnotation(m.annotations, "Scheduled", SPRING_SCHEDULED);
                if (scheduled != null) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    String cron = firstMemberValue(scheduled, "cron");
                    if (!cron.isBlank()) tags.put(TAG_CRON, cron);
                    String fd = firstMemberValue(scheduled, "fixedDelay", "fixedDelayString");
                    if (!fd.isBlank()) tags.put(TAG_FIXED_DELAY, fd);
                    String fr = firstMemberValue(scheduled, "fixedRate", "fixedRateString");
                    if (!fr.isBlank()) tags.put(TAG_FIXED_RATE, fr);
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_SCHEDULED_JOB, tags));
                }

                // EJB schedules - treat as scheduled job
                if (hasAnnotation(m.annotations, "Schedule", EJB_SCHEDULE_1, EJB_SCHEDULE_2)
                        || hasAnnotation(m.annotations, "Timeout", EJB_TIMEOUT_1, EJB_TIMEOUT_2)) {
                    Map<String, String> tags = new LinkedHashMap<>();
                    // For @Schedule, common members are cron-like pieces (hour, minute, second, etc).
                    // We keep best-effort: if 'info' exists, store it.
                    JAnnotationUse sch = firstAnnotation(m.annotations, "Schedule", EJB_SCHEDULE_1, EJB_SCHEDULE_2);
                    if (sch != null) {
                        String info = firstMemberValue(sch, "info");
                        if (!info.isBlank()) tags.put(TAG_PREFIX + "info", info);
                    }
                    out.add(new JRuntimeAnnotation(methodKey(t, m), ST_SCHEDULED_JOB, tags));
                }
            }
        }

        if (!out.isEmpty()) {
            model.runtimeAnnotations.addAll(out);
        }
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
        if (anns == null || anns.isEmpty()) return null;
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

    private static String firstMemberValue(JAnnotationUse a, String... keys) {
        if (a == null || a.values == null || a.values.isEmpty() || keys == null) return "";
        for (String k : keys) {
            if (k == null) continue;
            String v = a.values.get(k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String normalizeCommaList(String s) {
        if (s == null) return "";
        // JavaParser extractor may store array-ish values like "{a,b}" or "[a,b]" or ""a""
        String cleaned = s.trim();
        cleaned = cleaned.replace("{", "").replace("}", "").replace("[", "").replace("]", "");
                // Remove any quote characters that may appear in annotation values
        cleaned = cleaned.replace("\"", "");
        // split by comma
        String[] parts = cleaned.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim();
            if (v.isEmpty()) continue;
            out.add(v);
        }
        return String.join(",", out);
    }

    private static String methodKey(JType t, JMethod m) {
        if (t == null || m == null) return "";
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
}

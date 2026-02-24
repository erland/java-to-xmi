package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JRuntimeAnnotation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessagingAndSchedulingExtractorTest {

    @Test
    void extractsKafkaJmsAndScheduledAsRuntimeAnnotations() throws Exception {
        Path root = Files.createTempDirectory("j2x-msg-");

        String src = ""
                + "package demo;\n"
                + "import org.springframework.kafka.annotation.KafkaListener;\n"
                + "import org.springframework.jms.annotation.JmsListener;\n"
                + "import org.springframework.scheduling.annotation.Scheduled;\n"
                + "public class Jobs {\n"
                + "  @KafkaListener(topics = {\"topicA\", \"topicB\"}, groupId=\"g1\")\n"
                + "  public void onKafka(String s) {}\n"
                + "  @JmsListener(destination = \"queue1\")\n"
                + "  public void onJms(String s) {}\n"
                + "  @Scheduled(cron = \"0 * * * * *\")\n"
                + "  public void tick() {}\n"
                + "}\n";
        Path p = root.resolve("Jobs.java");
        Files.writeString(p, src, StandardCharsets.UTF_8);

        // SourceScanner is a static utility; use empty excludes and includeTests=false for these temp fixtures.
        List<Path> files = SourceScanner.scan(root, List.of(), false);
        JModel model = new JavaExtractor().extract(root, files);

        assertNotNull(model.runtimeAnnotations);

        JRuntimeAnnotation kafka = model.runtimeAnnotations.stream()
                .filter(a -> "MessageConsumer".equals(a.stereotype))
                .filter(a -> a.targetKey.contains("#onKafka("))
                .findFirst().orElse(null);
        assertNotNull(kafka);
        assertEquals("topicA,topicB", kafka.tags.get("runtime.destination"));
        assertEquals("topic", kafka.tags.get("runtime.destinationType"));
        assertEquals("g1", kafka.tags.get("runtime.groupId"));

        JRuntimeAnnotation jms = model.runtimeAnnotations.stream()
                .filter(a -> "MessageConsumer".equals(a.stereotype))
                .filter(a -> a.targetKey.contains("#onJms("))
                .findFirst().orElse(null);
        assertNotNull(jms);
        assertEquals("queue1", jms.tags.get("runtime.destination"));
        assertEquals("queue", jms.tags.get("runtime.destinationType"));

        JRuntimeAnnotation scheduled = model.runtimeAnnotations.stream()
                .filter(a -> "ScheduledJob".equals(a.stereotype))
                .filter(a -> a.targetKey.contains("#tick("))
                .findFirst().orElse(null);
        assertNotNull(scheduled);
        assertEquals("0 * * * * *", scheduled.tags.get("runtime.cron"));
    }

    @Test
    void extractsMicroprofileIncomingOutgoing() throws Exception {
        Path root = Files.createTempDirectory("j2x-mp-");

        String src = ""
                + "package demo;\n"
                + "import org.eclipse.microprofile.reactive.messaging.Incoming;\n"
                + "import org.eclipse.microprofile.reactive.messaging.Outgoing;\n"
                + "public class Flow {\n"
                + "  @Incoming(\"in\")\n"
                + "  public void consume(String s) {}\n"
                + "  @Outgoing(\"out\")\n"
                + "  public String produce() { return \"x\"; }\n"
                + "}\n";
        Path p = root.resolve("Flow.java");
        Files.writeString(p, src, StandardCharsets.UTF_8);

        // SourceScanner is a static utility; use empty excludes and includeTests=false for these temp fixtures.
        List<Path> files = SourceScanner.scan(root, List.of(), false);
        JModel model = new JavaExtractor().extract(root, files);

        JRuntimeAnnotation incoming = model.runtimeAnnotations.stream()
                .filter(a -> "MessageConsumer".equals(a.stereotype))
                .filter(a -> a.targetKey.contains("#consume("))
                .findFirst().orElse(null);
        assertNotNull(incoming);
        assertEquals("in", incoming.tags.get("runtime.destination"));
        assertEquals("channel", incoming.tags.get("runtime.destinationType"));

        JRuntimeAnnotation outgoing = model.runtimeAnnotations.stream()
                .filter(a -> "MessageProducer".equals(a.stereotype))
                .filter(a -> a.targetKey.contains("#produce("))
                .findFirst().orElse(null);
        assertNotNull(outgoing);
        assertEquals("out", outgoing.tags.get("runtime.destination"));
        assertEquals("channel", outgoing.tags.get("runtime.destinationType"));
    }
}

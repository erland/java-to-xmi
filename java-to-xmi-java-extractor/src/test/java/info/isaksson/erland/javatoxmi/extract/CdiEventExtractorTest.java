package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JRuntimeRelation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CdiEventExtractorTest {

    @Test
    void extractsCdiFireAndObserveAsRuntimeRelations() throws Exception {
        Path root = Files.createTempDirectory("j2x-cdi-");
        Path pkg = root.resolve("com/example/cdi");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("EventType.java"), """
                package com.example.cdi;
                public class EventType { }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("Publisher.java"), """
                package com.example.cdi;

                import jakarta.enterprise.event.Event;
                import jakarta.inject.Inject;

                public class Publisher {
                  @Inject Event<EventType> events;

                  public void publish() {
                    events.fire(new EventType());
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("Observer.java"), """
                package com.example.cdi;

                import jakarta.enterprise.event.Observes;
                import jakarta.enterprise.event.ObservesAsync;

                public class Observer {
                  public void on(@Observes EventType evt) { }
                  public void onAsync(@ObservesAsync EventType evt) { }
                }
                """, StandardCharsets.UTF_8);

        List<Path> files = SourceScanner.scan(root, List.of(), false);
        JModel model = new JavaExtractor().extract(root, files);

        // Expect: 1 fires + 2 observes
        assertEquals(3, model.runtimeRelations.size(), "runtimeRelations: " + model.runtimeRelations);

        JRuntimeRelation fires = model.runtimeRelations.stream()
                .filter(r -> "FiresEvent".equals(r.stereotype))
                .findFirst().orElse(null);
        assertNotNull(fires);
        assertEquals("com.example.cdi.Publisher", fires.sourceQualifiedName);
        assertEquals("com.example.cdi.EventType", fires.targetQualifiedName);

        long observesCount = model.runtimeRelations.stream()
                .filter(r -> "ObservesEvent".equals(r.stereotype))
                .count();
        assertEquals(2, observesCount);

        // Async observe should carry runtime.async=true
        JRuntimeRelation asyncObs = model.runtimeRelations.stream()
                .filter(r -> "ObservesEvent".equals(r.stereotype) && "true".equals(r.tags.get("runtime.async")))
                .findFirst().orElse(null);
        assertNotNull(asyncObs);
        assertEquals("com.example.cdi.Observer", asyncObs.sourceQualifiedName);
        assertEquals("com.example.cdi.EventType", asyncObs.targetQualifiedName);
    }
}

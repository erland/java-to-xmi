package info.isaksson.erland.javatoxmi.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke test that exercises the "architecture browse" features:
 * REST endpoints, CDI events/observers, interceptors/transactions, messaging/scheduling,
 * Flyway migrations as artifacts, and JPMS module boundaries.
 */
public class ArchitectureBrowseSmokeTest {

    @Test
    void generatesXmiContainingHighRoiRuntimeSemantics() throws Exception {
        Path root = Files.createTempDirectory("j2x-smoke-");

        // --- Java sources ---
        Path src = root.resolve("src/main/java");
        Files.createDirectories(src);

        // JPMS module descriptor
        Files.writeString(src.resolve("module-info.java"), """
                module com.example.app {
                  requires java.sql;
                  exports com.example.api;
                }
                """
        );

        Path apiPkg = src.resolve("com/example/api");
        Files.createDirectories(apiPkg);

        Files.writeString(apiPkg.resolve("EventType.java"), """
                package com.example.api;
                public class EventType { }
                """
        );

        // JAX-RS resource
        Files.writeString(apiPkg.resolve("ApiResource.java"), """
                package com.example.api;

                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.Path;
                import jakarta.ws.rs.Produces;

                @Path(\"/api\")
                public class ApiResource {
                  @GET
                  @Path(\"/ping\")
                  @Produces(\"text/plain\")
                  public String ping() { return \"ok\"; }
                }
                """
        );

        // CDI publisher (Event<T>.fire)
        Files.writeString(apiPkg.resolve("Publisher.java"), """
                package com.example.api;

                import jakarta.enterprise.event.Event;
                import jakarta.inject.Inject;

                public class Publisher {
                  @Inject Event<EventType> event;
                  public void publish() { event.fire(new EventType()); }
                }
                """
        );

        // CDI observers
        Files.writeString(apiPkg.resolve("Observer.java"), """
                package com.example.api;

                import jakarta.enterprise.event.Observes;
                import jakarta.enterprise.event.ObservesAsync;

                public class Observer {
                  public void onEvt(@Observes EventType evt) { }
                  public void onEvtAsync(@ObservesAsync EventType evt) { }
                }
                """
        );

        // Interceptor + transactional + scheduled + kafka listener (Spring)
        Path implPkg = src.resolve("com/example/impl");
        Files.createDirectories(implPkg);

        Files.writeString(implPkg.resolve("AuditBinding.java"), """
                package com.example.impl;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.METHOD})
                public @interface AuditBinding { }
                """
        );

        Files.writeString(implPkg.resolve("AuditInterceptor.java"), """
                package com.example.impl;

                import jakarta.interceptor.AroundInvoke;
                import jakarta.interceptor.Interceptor;
                import jakarta.interceptor.InvocationContext;

                @Interceptor
                @AuditBinding
                public class AuditInterceptor {
                  @AroundInvoke
                  public Object around(InvocationContext ctx) throws Exception {
                    return ctx.proceed();
                  }
                }
                """
        );

        Files.writeString(implPkg.resolve("Service.java"), """
                package com.example.impl;

                import jakarta.transaction.Transactional;
                import org.springframework.kafka.annotation.KafkaListener;
                import org.springframework.scheduling.annotation.Scheduled;

                @Transactional
                public class Service {

                  @KafkaListener(topics=\"topic-a\", groupId=\"g1\")
                  public void onMessage(String msg) { }

                  @Scheduled(cron=\"0 0 * * * *\")
                  public void tick() { }

                  @Transactional
                  public void doWork() { }
                }
                """
        );

        // --- Flyway migrations ---
        Path mig = root.resolve("src/main/resources/db/migration");
        Files.createDirectories(mig);
        Files.writeString(mig.resolve("V1__init.sql"), "create table t(id int);");
        Files.writeString(mig.resolve("R__refresh.sql"), "select 1;");

        // Run pipeline
        JavaToXmiOptions opt = new JavaToXmiOptions();
        opt.modelName = "smoke";
        opt.includeStereotypes = true;
        opt.includeDependencies = true;

        JavaToXmiResult res = new JavaToXmiService().generateFromSource(root, List.of(), opt);
        assertNotNull(res);
        assertNotNull(res.xmiString);
        String xmi = res.xmiString;

        // Runtime stereotypes should be injected (post-processing strategy)
        assertTrue(xmi.contains("JavaAnnotations:FiresEvent"), "Expected FiresEvent stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:ObservesEvent"), "Expected ObservesEvent stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:RestResource"), "Expected RestResource stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:RestOperation"), "Expected RestOperation stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:Interceptor"), "Expected Interceptor stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:Transactional"), "Expected Transactional stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:MessageConsumer"), "Expected MessageConsumer stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:ScheduledJob"), "Expected ScheduledJob stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:FlywayMigration"), "Expected FlywayMigration stereotype application");
        assertTrue(xmi.contains("JavaAnnotations:JavaModule"), "Expected JavaModule stereotype application");

        // Runtime tags are persisted as EAnnotation details; assert at least a few are present in text.
        assertTrue(xmi.contains("runtime.path") || xmi.contains("runtime.httpMethod"), "Expected REST runtime tags to be persisted");
        assertTrue(xmi.contains("runtime.migration") || xmi.contains("flyway"), "Expected migration metadata to be present");
    }
}

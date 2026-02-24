package info.isaksson.erland.javatoxmi.extract;

import info.isaksson.erland.javatoxmi.io.SourceScanner;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JRuntimeAnnotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RestEndpointExtractorTest {

    @Test
    void extractsJaxRsAndSpringEndpointsAsRuntimeAnnotations() throws Exception {
        Path root = Files.createTempDirectory("j2x-rest-");

        Path pkg = root.resolve("com/example/api");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("JaxrsResource.java"), """
                package com.example.api;

                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.Path;
                import jakarta.ws.rs.Produces;

                @Path(\"/api\")
                public class JaxrsResource {
                  @GET
                  @Path(\"/hello\")
                  @Produces(\"text/plain\")
                  public String hello() { return \"x\"; }
                }
                """, java.nio.charset.StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("SpringResource.java"), """
                package com.example.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(\"/v1\")
                public class SpringResource {
                  @GetMapping(\"/ping\")
                  public String ping() { return \"ok\"; }
                }
                """, java.nio.charset.StandardCharsets.UTF_8);

        List<Path> files = SourceScanner.scan(root, List.of(), false);
        assertTrue(files.size() >= 2);

        JModel model = new JavaExtractor().extract(root, files);

        // Expect: 2 class annotations + 2 method annotations
        assertEquals(4, model.runtimeAnnotations.size(), "runtimeAnnotations: " + model.runtimeAnnotations);

        // JAX-RS class
        JRuntimeAnnotation jaxrsClass = model.runtimeAnnotations.stream()
                .filter(a -> "com.example.api.JaxrsResource".equals(a.targetKey))
                .findFirst().orElse(null);
        assertNotNull(jaxrsClass);
        assertEquals("RestResource", jaxrsClass.stereotype);
        assertEquals("/api", jaxrsClass.tags.get("runtime.path"));

        // JAX-RS method
        JRuntimeAnnotation jaxrsHello = model.runtimeAnnotations.stream()
                .filter(a -> a.targetKey != null && a.targetKey.startsWith("com.example.api.JaxrsResource#hello("))
                .findFirst().orElse(null);
        assertNotNull(jaxrsHello);
        assertEquals("RestOperation", jaxrsHello.stereotype);
        assertEquals("/api/hello", jaxrsHello.tags.get("runtime.path"));
        assertEquals("GET", jaxrsHello.tags.get("runtime.httpMethod"));
        assertEquals("text/plain", jaxrsHello.tags.get("runtime.produces"));

        // Spring class
        JRuntimeAnnotation springClass = model.runtimeAnnotations.stream()
                .filter(a -> "com.example.api.SpringResource".equals(a.targetKey))
                .findFirst().orElse(null);
        assertNotNull(springClass);
        assertEquals("RestResource", springClass.stereotype);
        assertEquals("/v1", springClass.tags.get("runtime.path"));

        // Spring method
        JRuntimeAnnotation springPing = model.runtimeAnnotations.stream()
                .filter(a -> a.targetKey != null && a.targetKey.startsWith("com.example.api.SpringResource#ping("))
                .findFirst().orElse(null);
        assertNotNull(springPing);
        assertEquals("RestOperation", springPing.stereotype);
        assertEquals("/v1/ping", springPing.tags.get("runtime.path"));
        assertEquals("GET", springPing.tags.get("runtime.httpMethod"));
    }
}

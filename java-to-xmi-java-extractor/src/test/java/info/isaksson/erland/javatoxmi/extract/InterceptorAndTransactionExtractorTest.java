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

public class InterceptorAndTransactionExtractorTest {

    @Test
    void extractsInterceptorAndTransactionalAsRuntimeAnnotations() throws Exception {
        Path root = Files.createTempDirectory("j2x-tx-");
        Path pkg = root.resolve("com/example");
        Files.createDirectories(pkg);

        Files.writeString(pkg.resolve("MyBinding.java"), """
                package com.example;

                import jakarta.interceptor.InterceptorBinding;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @InterceptorBinding
                @Retention(RetentionPolicy.RUNTIME)
                public @interface MyBinding {}
                """, StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("MyInterceptor.java"), """
                package com.example;

                import jakarta.annotation.Priority;
                import jakarta.interceptor.AroundInvoke;
                import jakarta.interceptor.Interceptor;
                import jakarta.interceptor.InvocationContext;

                @MyBinding
                @Interceptor
                @Priority(1)
                public class MyInterceptor {
                  @AroundInvoke
                  public Object around(InvocationContext ctx) throws Exception {
                    return ctx.proceed();
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("Service.java"), """
                package com.example;

                import jakarta.transaction.Transactional;

                @Transactional(Transactional.TxType.REQUIRES_NEW)
                public class Service {
                  @Transactional
                  public void doWork() {}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(pkg.resolve("SpringService.java"), """
                package com.example;

                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;

                public class SpringService {
                  @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
                  public void run() {}
                }
                """, StandardCharsets.UTF_8);

        List<Path> files = SourceScanner.scan(root, List.of(), false);
        assertTrue(files.size() >= 4);

        JModel model = new JavaExtractor().extract(root, files);

        // Interceptor class annotation
        JRuntimeAnnotation interceptor = model.runtimeAnnotations.stream()
                .filter(a -> "com.example.MyInterceptor".equals(a.targetKey))
                .findFirst().orElse(null);
        assertNotNull(interceptor, "Expected interceptor runtime annotation");
        assertEquals("Interceptor", interceptor.stereotype);
        assertTrue(interceptor.tags.getOrDefault("runtime.bindings", "").contains("MyBinding"));

        // Transactional class
        JRuntimeAnnotation svcClass = model.runtimeAnnotations.stream()
                .filter(a -> "com.example.Service".equals(a.targetKey) && "Transactional".equals(a.stereotype))
                .findFirst().orElse(null);
        assertNotNull(svcClass, "Expected transactional class runtime annotation");
        assertEquals("true", svcClass.tags.get("runtime.transactional"));
        assertNotNull(svcClass.tags.get("runtime.tx.propagation"), "Expected propagation tag");

        // Transactional method (jakarta)
        JRuntimeAnnotation svcMethod = model.runtimeAnnotations.stream()
                .filter(a -> a.targetKey != null && a.targetKey.startsWith("com.example.Service#doWork(") && "Transactional".equals(a.stereotype))
                .findFirst().orElse(null);
        assertNotNull(svcMethod, "Expected transactional method runtime annotation");
        assertEquals("true", svcMethod.tags.get("runtime.transactional"));

        // Transactional method (spring)
        JRuntimeAnnotation springMethod = model.runtimeAnnotations.stream()
                .filter(a -> a.targetKey != null && a.targetKey.startsWith("com.example.SpringService#run(") && "Transactional".equals(a.stereotype))
                .findFirst().orElse(null);
        assertNotNull(springMethod, "Expected spring transactional method runtime annotation");
        assertEquals("true", springMethod.tags.get("runtime.transactional"));
        assertEquals("true", springMethod.tags.get("runtime.tx.readOnly"));
        assertEquals("REQUIRED", springMethod.tags.get("runtime.tx.propagation"));
    }
}

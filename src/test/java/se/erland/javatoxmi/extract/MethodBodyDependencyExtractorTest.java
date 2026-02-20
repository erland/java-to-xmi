package se.erland.javatoxmi.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal safety-net test for conservative dependency extraction from method bodies.
 *
 * <p>Note: MethodBodyDependencyExtractor is package-private by design; this test lives in the same package.</p>
 */
public class MethodBodyDependencyExtractorTest {

    @Test
    void extractsConstructorStaticLocalParamAndThisFieldDepsAndSkipsJavaLangAndSelf() throws Exception {
        String code = """
                package p;

                class A {
                    Service svc;
                    void m(Service param) {
                        Service local = param;
                        this.svc.run();
                        param.run();
                        local.run();
                        new Other();
                        Util.doIt();
                        System.out.println(\"x\");
                        new A();
                    }
                }

                class Service { void run() {} }
                class Other {}
                class Util { static void doIt() {} }
                """;

        // Parse
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setCharacterEncoding(StandardCharsets.UTF_8);
        JavaParser parser = new JavaParser(cfg);
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        ClassOrInterfaceDeclaration a = cu.findFirst(ClassOrInterfaceDeclaration.class, c -> "A".equals(c.getNameAsString()))
                .orElseThrow();
        MethodDeclaration md = a.getMethodsByName("m").get(0);

        // Minimal model + context
        Path tmp = Files.createTempDirectory("j2x-bodydeps");
        JModel model = new JModel(tmp, List.of());
        Set<String> projectTypes = Set.of("p.A", "p.Service", "p.Other", "p.Util");
        ImportContext ctx = ImportContext.from(cu, "p", projectTypes);

        Map<String, Map<String, String>> nestedByOuter = Map.of();
        List<String> nestedScopeChain = List.of("p.A");
        Map<String, String> fieldTypeByName = new HashMap<>();
        fieldTypeByName.put("svc", "p.Service");

        Set<String> deps = MethodBodyDependencyExtractor.extract(
                md,
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model,
                "p.A",
                fieldTypeByName
        );

        // Expected conservative deps.
        assertTrue(deps.contains("p.Service"), "Expected Service dependency but got: " + deps);
        assertTrue(deps.contains("p.Other"), "Expected Other dependency but got: " + deps);
        assertTrue(deps.contains("p.Util"), "Expected Util dependency but got: " + deps);

        // Should not include java.lang/System or other JDK types.
        assertFalse(deps.stream().anyMatch(d -> d != null && d.startsWith("java.")), "Did not expect java.* deps but got: " + deps);
        // Should remove self-deps.
        assertFalse(deps.contains("p.A"), "Did not expect self dependency but got: " + deps);
    }
}

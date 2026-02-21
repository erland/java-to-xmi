package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterisation/edge tests for best-effort type resolution without symbol solving.
 */
public class TypeResolverEdgeTest {

    @Test
    void explicitImport_winsOverWildcardImport() {
        Set<String> project = Set.of(
                "p.Owner",
                "com.exp.Foo",
                "com.wild.Foo"
        );
        ImportContext ctx = ImportContext.from(null, "p", project);
        ctx.explicitImportsBySimple.put("Foo", "com.exp.Foo");
        ctx.wildcardImports.add("com.wild");

        String resolved = TypeResolver.resolveWithNestedScope(
                "Foo",
                ctx,
                Map.of(),
                List.of("p.Owner")
        );

        assertEquals("com.exp.Foo", resolved);
    }

    @Test
    void samePackageType_isResolved() {
        Set<String> project = Set.of(
                "a.b.Bar",
                "a.b.Owner"
        );
        ImportContext ctx = ImportContext.from(null, "a.b", project);

        String resolved = TypeResolver.resolveWithNestedScope(
                "Bar",
                ctx,
                Map.of(),
                List.of("a.b.Owner")
        );

        assertEquals("a.b.Bar", resolved);
    }

    @Test
    void nestedScope_prefersInnerTypeInEnclosingChain() {
        Set<String> project = Set.of(
                "p.Outer",
                "p.Outer.Inner",
                "p.Other.Inner",
                "p.Owner"
        );
        ImportContext ctx = ImportContext.from(null, "p", project);

        Map<String, Map<String, String>> nestedByOuter = new HashMap<>();
        nestedByOuter.put("p.Outer", Map.of("Inner", "p.Outer.Inner"));

        String resolved = TypeResolver.resolveWithNestedScope(
                "Inner",
                ctx,
                nestedByOuter,
                List.of("p.Owner", "p.Outer")
        );

        assertEquals("p.Outer.Inner", resolved);
    }

    @Test
    void ambiguousWildcardImports_chooseFirstDeterministically() {
        Set<String> project = Set.of(
                "p.Owner",
                "x.one.Foo",
                "x.two.Foo"
        );
        ImportContext ctx = ImportContext.from(null, "p", project);
        ctx.wildcardImports.add("x.one");
        ctx.wildcardImports.add("x.two");

        String resolved = TypeResolver.resolveWithNestedScope(
                "Foo",
                ctx,
                Map.of(),
                List.of("p.Owner")
        );

        assertEquals("x.one.Foo", resolved, "Wildcard import order should be deterministic");
    }

    @Test
    void unresolvedSimpleName_isRecordedAndLeftAsRendered() {
        Set<String> project = Set.of("p.Owner");
        ImportContext ctx = ImportContext.from(null, "p", project);
        JModel model = new JModel(java.nio.file.Path.of("."), List.of());

        String rendered = TypeResolver.resolveTypeRef(
                StaticJavaParser.parseType("Missing"),
                ctx,
                Map.of(),
                List.of("p.Owner"),
                model,
                "p.Owner",
                "field 'x'"
        );

        // The resolver applies Java's implicit java.lang.* lookup before declaring something unresolved.
        assertEquals("java.lang.Missing", rendered);
        // java.lang.* candidates are treated as "external" refs in this resolver (not "unresolved"),
        // because they are qualified via implicit java.lang lookup.
        assertTrue(
                model.externalTypeRefs.stream().anyMatch(u -> u.referencedType.endsWith("Missing")),
                "Expected Missing to be recorded as an external type ref (java.lang.*)"
        );
    }
}

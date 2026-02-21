package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import se.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Extracts {@link JType} instances from parsed compilation units using the pre-built {@link ProjectTypeIndex}.
 */
final class TypeExtractionEngine {

    private TypeExtractionEngine() {}

    static void extractAllTypes(JModel model, List<ParsedUnit> units, ProjectTypeIndex index, boolean includeDependencies) {
        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            ImportContext ctx = ImportContext.from(u.cu, pkg, index.projectTypeQualifiedNames);

            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;
                extractTypeRecursive(model, ctx, index.nestedByOuter, pkg, td, null, null, List.of(), includeDependencies);
            }
        }
    }

    private static void extractTypeRecursive(
            JModel model,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            String pkg,
            TypeDeclaration<?> td,
            String outerQn,
            String outerPathFromTop,
            List<String> enclosingScopeChain,
            boolean includeDependencies
    ) {
        String name = td.getNameAsString();
        String pathFromTop = (outerPathFromTop == null || outerPathFromTop.isBlank())
                ? name
                : outerPathFromTop + "." + name;
        String qn = qualifiedName(pkg, pathFromTop);

        // Within this type, simple names should resolve to nested member types declared in this type,
        // as well as those in any enclosing types.
        List<String> scopeChain = new ArrayList<>(enclosingScopeChain);
        scopeChain.add(qn);

        extractOneType(model, ctx, nestedByOuter, scopeChain, pkg, td, outerQn, outerPathFromTop, includeDependencies);

        // Recurse into nested member types
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof TypeDeclaration)) continue;
            TypeDeclaration<?> child = (TypeDeclaration<?>) member;
            if (!isSupportedType(child)) continue;
            extractTypeRecursive(model, ctx, nestedByOuter, pkg, child, qn, pathFromTop, scopeChain, includeDependencies);
        }
    }

    private static void extractOneType(
            JModel model,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            String pkg,
            TypeDeclaration<?> td,
            String outerQn,
            String outerPathFromTop,
            boolean includeDependencies
    ) {
        TypeHeaderExtractor.TypeHeader header = TypeHeaderExtractor.extract(
                pkg,
                td,
                outerPathFromTop,
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model
        );

        FieldExtractor.FieldExtraction fieldExtraction = FieldExtractor.extract(
                td,
                header.typeParams(),
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model,
                header.qn()
        );

        MethodExtractor.MethodExtraction methodExtraction = MethodExtractor.extract(
                td,
                header.typeParams(),
                ctx,
                nestedByOuter,
                nestedScopeChain,
                model,
                header.qn(),
                fieldExtraction.fieldTypeByName(),
                includeDependencies
        );

        // If JPA relationship annotations are placed on getter methods (property access), propagate them to fields.
        JavaExtractor.propagateJpaRelationshipAnnotationsFromGettersToFields(fieldExtraction.fields(), methodExtraction.methods());

        model.types.add(new JType(
                pkg,
                header.name(),
                header.qn(),
                outerQn,
                header.kind(),
                header.visibility(),
                header.isAbstract(),
                header.isStatic(),
                header.isFinal(),
                header.extendsType(),
                header.implementsTypes(),
                header.annotations(),
                header.doc(),
                fieldExtraction.fields(),
                methodExtraction.methods(),
                header.enumLiterals(),
                methodExtraction.sortedBodyDependencies()
        ));
    }

    static String extractTypeDoc(TypeDeclaration<?> td) {
        // Prefer raw comment content (keeps tags/HTML "as-is"), but normalize leading '*' markers.
        return td.getJavadocComment()
                .map(jc -> JavaExtractor.normalizeDocContent(jc.getContent()))
                .orElse("");
    }

    static boolean isSupportedType(TypeDeclaration<?> td) {
        return (td instanceof ClassOrInterfaceDeclaration
                || td instanceof EnumDeclaration
                || td instanceof AnnotationDeclaration);
    }

    static List<BodyDeclaration<?>> getMembers(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) td).getMembers();
        }
        if (td instanceof EnumDeclaration) {
            return ((EnumDeclaration) td).getMembers();
        }
        if (td instanceof AnnotationDeclaration) {
            return ((AnnotationDeclaration) td).getMembers();
        }
        return Collections.emptyList();
    }

    static String qualifiedName(String pkg, String name) {
        if (pkg == null || pkg.isBlank()) return name;
        return pkg + "." + name;
    }

    static JTypeKind kindOf(TypeDeclaration<?> td) {
        if (td instanceof AnnotationDeclaration) return JTypeKind.ANNOTATION;
        if (td instanceof EnumDeclaration) return JTypeKind.ENUM;
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            return cid.isInterface() ? JTypeKind.INTERFACE : JTypeKind.CLASS;
        }
        return JTypeKind.CLASS;
    }

    static boolean hasModifier(Object node, Modifier.Keyword kw) {
        if (node instanceof NodeWithModifiers) {
            @SuppressWarnings("rawtypes")
            NodeWithModifiers nwm = (NodeWithModifiers) node;
            return nwm.hasModifier(kw);
        }
        return false;
    }

    static JVisibility visibilityOf(NodeWithModifiers<?> node) {
        if (node.hasModifier(Modifier.Keyword.PUBLIC)) return JVisibility.PUBLIC;
        if (node.hasModifier(Modifier.Keyword.PROTECTED)) return JVisibility.PROTECTED;
        if (node.hasModifier(Modifier.Keyword.PRIVATE)) return JVisibility.PRIVATE;
        return JVisibility.PACKAGE_PRIVATE;
    }
}

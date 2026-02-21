package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.Modifier;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JTypeKind;
import se.erland.javatoxmi.model.JVisibility;

import java.util.*;

/**
 * Extracts type-level metadata (kind, visibility/modifiers, supertypes, annotations/docs, type parameters, enum literals).
 *
 * This class is intentionally focused on declaration/header concerns only (no members like fields/methods).
 */
final class TypeHeaderExtractor {

    private TypeHeaderExtractor() {}

    record TypeHeader(
            String name,
            String qn,
            JTypeKind kind,
            JVisibility visibility,
            boolean isAbstract,
            boolean isStatic,
            boolean isFinal,
            String extendsType,
            List<String> implementsTypes,
            List<JAnnotationUse> annotations,
            String doc,
            Set<String> typeParams,
            List<String> enumLiterals
    ) {}

    static TypeHeader extract(
            String pkg,
            TypeDeclaration<?> td,
            String outerPathFromTop,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            JModel model
    ) {
        JTypeKind kind = TypeExtractionEngine.kindOf(td);
        JVisibility vis = TypeExtractionEngine.visibilityOf(td);
        boolean isAbstract = TypeExtractionEngine.hasModifier(td, Modifier.Keyword.ABSTRACT);
        boolean isStatic = TypeExtractionEngine.hasModifier(td, Modifier.Keyword.STATIC);
        boolean isFinal = TypeExtractionEngine.hasModifier(td, Modifier.Keyword.FINAL);

        String name = td.getNameAsString();
        String qn = TypeExtractionEngine.qualifiedName(
                pkg,
                (outerPathFromTop == null || outerPathFromTop.isBlank()) ? name : outerPathFromTop + "." + name
        );

        // Type-level annotations
        List<JAnnotationUse> annotations = AnnotationExtractor.extract(td, ctx);

        // Type-level JavaDoc (best-effort)
        String doc = TypeExtractionEngine.extractTypeDoc(td);

        // Type parameters visible within the type declaration
        Set<String> typeParams = extractTypeParams(td);

        // Extends / implements
        String extendsType = null;
        List<String> implementsTypes = new ArrayList<>();
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (!cid.getExtendedTypes().isEmpty()) {
                extendsType = TypeResolver.resolveTypeRef(
                        cid.getExtendedTypes().get(0),
                        ctx,
                        nestedByOuter,
                        nestedScopeChain,
                        model,
                        qn,
                        "extends"
                );
            }
            for (ClassOrInterfaceType it : cid.getImplementedTypes()) {
                implementsTypes.add(TypeResolver.resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
            }
        } else if (td instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) td;
            // enums can implement interfaces
            for (ClassOrInterfaceType it : ed.getImplementedTypes()) {
                implementsTypes.add(TypeResolver.resolveTypeRef(it, ctx, nestedByOuter, nestedScopeChain, model, qn, "implements"));
            }
        }

        // Enum literals
        List<String> enumLiterals = extractEnumLiterals(td);

        return new TypeHeader(
                name,
                qn,
                kind,
                vis,
                isAbstract,
                isStatic,
                isFinal,
                extendsType,
                implementsTypes,
                annotations,
                doc,
                typeParams,
                enumLiterals
        );
    }

    private static Set<String> extractTypeParams(TypeDeclaration<?> td) {
        Set<String> typeParams = new HashSet<>();
        if (td instanceof ClassOrInterfaceDeclaration) {
            for (TypeParameter tp : ((ClassOrInterfaceDeclaration) td).getTypeParameters()) {
                if (tp == null) continue;
                String n = tp.getNameAsString();
                if (n != null && !n.isBlank()) typeParams.add(n);
            }
        }
        return typeParams;
    }

    private static List<String> extractEnumLiterals(TypeDeclaration<?> td) {
        if (!(td instanceof EnumDeclaration)) return List.of();
        EnumDeclaration ed = (EnumDeclaration) td;
        List<String> out = new ArrayList<>();
        for (EnumConstantDeclaration ecd : ed.getEntries()) {
            out.add(ecd.getNameAsString());
        }
        return out;
    }
}

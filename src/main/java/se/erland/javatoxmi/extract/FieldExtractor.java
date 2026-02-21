package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import se.erland.javatoxmi.model.*;

import java.util.*;

/**
 * Extracts field-level members into {@link JField} instances, and produces a quick name->type lookup map
 * used for method-body dependency extraction (e.g., resolving "this.field" calls).
 */
final class FieldExtractor {

    private FieldExtractor() {}

    record FieldExtraction(List<JField> fields, Map<String, String> fieldTypeByName) {}

    static FieldExtraction extract(
            TypeDeclaration<?> td,
            Set<String> enclosingTypeParams,
            ImportContext ctx,
            Map<String, Map<String, String>> nestedByOuter,
            List<String> nestedScopeChain,
            JModel model,
            String ownerQn
    ) {
        List<JField> fields = new ArrayList<>();
        Map<String, String> fieldTypeByName = new HashMap<>();

        for (BodyDeclaration<?> member : TypeExtractionEngine.getMembers(td)) {
            if (!(member instanceof FieldDeclaration)) continue;
            FieldDeclaration fd = (FieldDeclaration) member;
            JVisibility fVis = TypeExtractionEngine.visibilityOf(fd);
            boolean fStatic = fd.isStatic();
            boolean fFinal = fd.isFinal();
            List<JAnnotationUse> fAnns = AnnotationExtractor.extract(fd, ctx);

            for (VariableDeclarator var : fd.getVariables()) {
                String name = var.getNameAsString();
                String fType = TypeResolver.resolveTypeRef(
                        var.getType(),
                        ctx,
                        nestedByOuter,
                        nestedScopeChain,
                        model,
                        ownerQn,
                        "field '" + name + "'"
                );
                TypeRef fTypeRef = TypeRefParser.parse(
                        var.getType(),
                        enclosingTypeParams,
                        ctx,
                        nestedByOuter,
                        nestedScopeChain,
                        model,
                        ownerQn,
                        "field '" + name + "'"
                );
                fields.add(new JField(name, fType, fTypeRef, fVis, fStatic, fFinal, fAnns));
                fieldTypeByName.put(name, fType);
            }
        }

        return new FieldExtraction(fields, fieldTypeByName);
    }
}

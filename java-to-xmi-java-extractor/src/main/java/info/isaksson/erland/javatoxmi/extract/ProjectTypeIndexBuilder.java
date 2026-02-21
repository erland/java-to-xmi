package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.body.*;

import java.util.*;

/**
 * Builds a project-local type index (qualified name -> stub) including nested member types.
 */
final class ProjectTypeIndexBuilder {

    private ProjectTypeIndexBuilder() {}

    static ProjectTypeIndex build(List<ParsedUnit> units) {
        // qualified name -> stub
        Map<String, TypeStub> projectTypes = new HashMap<>();
        // outerQualifiedName -> (nestedSimpleName -> nestedQualifiedName)
        Map<String, Map<String, String>> nestedByOuter = new HashMap<>();
        List<TypeInfo> allTypeInfos = new ArrayList<>();

        for (ParsedUnit u : units) {
            String pkg = u.cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            for (TypeDeclaration<?> td : u.cu.getTypes()) {
                if (!isSupportedType(td)) continue;
                collectTypeInfosRecursive(allTypeInfos, nestedByOuter, pkg, td, null, td.getNameAsString());
            }
        }

        for (TypeInfo ti : allTypeInfos) {
            projectTypes.put(ti.qualifiedName(), new TypeStub(ti.qualifiedName(), ti.packageName(), ti.simpleName()));
        }

        return new ProjectTypeIndex(projectTypes, nestedByOuter, Set.copyOf(projectTypes.keySet()));
    }

    private static void collectTypeInfosRecursive(
            List<TypeInfo> out,
            Map<String, Map<String, String>> nestedByOuter,
            String pkg,
            TypeDeclaration<?> td,
            String outerQn,
            String pathFromTop
    ) {
        if (!isSupportedType(td)) return;
        String simpleName = td.getNameAsString();
        String qn = qualifiedName(pkg, pathFromTop);
        out.add(new TypeInfo(pkg, simpleName, qn, outerQn, td));

        // Collect nested member types recursively
        for (BodyDeclaration<?> member : getMembers(td)) {
            if (!(member instanceof TypeDeclaration)) continue;
            TypeDeclaration<?> child = (TypeDeclaration<?>) member;
            if (!isSupportedType(child)) continue;
            String childName = child.getNameAsString();
            String childPath = pathFromTop + "." + childName;
            String childQn = qualifiedName(pkg, childPath);

            nestedByOuter
                    .computeIfAbsent(qn, __ -> new HashMap<>())
                    .put(childName, childQn);

            collectTypeInfosRecursive(out, nestedByOuter, pkg, child, qn, childPath);
        }
    }

    private static boolean isSupportedType(TypeDeclaration<?> td) {
        return (td instanceof ClassOrInterfaceDeclaration
                || td instanceof EnumDeclaration
                || td instanceof AnnotationDeclaration);
    }

    private static List<BodyDeclaration<?>> getMembers(TypeDeclaration<?> td) {
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

    private static String qualifiedName(String pkg, String name) {
        if (pkg == null || pkg.isBlank()) return name;
        return pkg + "." + name;
    }
}

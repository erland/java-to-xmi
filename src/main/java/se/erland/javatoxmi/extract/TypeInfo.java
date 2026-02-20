package se.erland.javatoxmi.extract;

import com.github.javaparser.ast.body.TypeDeclaration;

/** Package-private carrier for discovered type declarations during indexing. */
record TypeInfo(
        String packageName,
        String simpleName,
        String qualifiedName,
        String outerQualifiedName,
        TypeDeclaration<?> declaration
) {}

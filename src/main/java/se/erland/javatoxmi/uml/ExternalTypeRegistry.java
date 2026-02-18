package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Type;

/**
 * Registry for creating/looking up stub types for references that are external to the scanned project.
 *
 * <p>This keeps {@code UmlClassifierBuilder} focused on creating in-model classifiers and delegating
 * external stub handling.</p>
 */
final class ExternalTypeRegistry {

    /**
     * Ensure there is a stub {@link Type} representing an external reference.
     *
     * <p>Creates the stub under a deterministic package hierarchy rooted at {@code _external}.
     * For example {@code java.time.Instant} becomes package {@code _external.java.time} and
     * type {@code Instant}.</p>
     */
    Type ensureExternalStub(UmlBuildContext ctx, String qualifiedName) {
        String base = qualifiedName == null ? "Object" : qualifiedName;
        base = UmlRelationBuilder.stripGenerics(base);
        if (base.endsWith("[]")) base = base.substring(0, base.length() - 2);

        String pkgName = "_external";
        String typeName = base;
        if (base.contains(".")) {
            int li = base.lastIndexOf('.');
            pkgName = "_external." + base.substring(0, li);
            typeName = base.substring(li + 1);
        }

        Package pkg = getOrCreatePackage(ctx, pkgName);
        Type existing = pkg.getOwnedType(typeName);
        if (existing != null) {
            return existing;
        }

        org.eclipse.uml2.uml.Class c = pkg.createOwnedClass(typeName, false);
        ctx.stats.externalStubsCreated++;
        UmlBuilderSupport.annotateId(c, "ExternalStub:" + base);
        return c;
    }

    private Package getOrCreatePackage(UmlBuildContext ctx, String packageName) {
        Model root = ctx.model;
        if (packageName == null || packageName.isBlank()) {
            return root;
        }

        Package cached = ctx.packageByName.get(packageName);
        if (cached != null) {
            return cached;
        }

        String[] parts = packageName.split("\\.");
        Package current = root;
        StringBuilder soFar = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) soFar.append('.');
            soFar.append(parts[i]);
            String q = soFar.toString();
            Package existing = ctx.packageByName.get(q);
            if (existing != null) {
                current = existing;
                continue;
            }
            Package created = current.createNestedPackage(parts[i]);
            ctx.stats.packagesCreated++;
            UmlBuilderSupport.annotateId(created, "Package:" + q);
            ctx.packageByName.put(q, created);
            current = created;
        }

        ctx.packageByName.put(packageName, current);
        return current;
    }
}

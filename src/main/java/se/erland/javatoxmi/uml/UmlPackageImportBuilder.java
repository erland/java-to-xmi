package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Package;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JMethod;
import se.erland.javatoxmi.model.JParam;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.TypeRef;

import java.util.HashSet;
import java.util.Set;

/**
 * Adds conservative package-to-package {@link org.eclipse.uml2.uml.PackageImport} relationships.
 *
 * <p>The intent is to expose high-level dependency structure in UML tools without introducing
 * a heavy-weight architecture model. Imports are derived from type usage (fields + signatures
 * + method-body dependency hints when enabled).</p>
 */
final class UmlPackageImportBuilder {

    void addPackageImports(UmlBuildContext ctx, JType ownerType, Classifier ownerClassifier) {
        if (ctx == null || ownerType == null || ownerClassifier == null) return;

        Package ownerPkg = ownerClassifier.getPackage();
        if (ownerPkg == null) ownerPkg = ownerClassifier.getModel();
        if (ownerPkg == null) return;

        Set<Package> referencedPackages = new HashSet<>();

        // extends/implements
        addRefByQName(ctx, referencedPackages, ownerType.extendsType);
        if (ownerType.implementsTypes != null) {
            for (String qn : ownerType.implementsTypes) addRefByQName(ctx, referencedPackages, qn);
        }

        // fields
        if (ownerType.fields != null) {
            for (JField f : ownerType.fields) {
                if (f == null) continue;
                addRefByTypeRef(ctx, referencedPackages, f.typeRef);
            }
        }

        // methods (signature)
        if (ownerType.methods != null) {
            for (JMethod m : ownerType.methods) {
                if (m == null) continue;
                addRefByTypeRef(ctx, referencedPackages, m.returnTypeRef);
                if (m.params != null) {
                    for (JParam p : m.params) {
                        if (p == null) continue;
                        addRefByTypeRef(ctx, referencedPackages, p.typeRef);
                    }
                }
            }
        }

        // conservative method-body dependency hints (already resolved to qualified names when possible)
        if (ctx.includeDependencies && ownerType.methodBodyTypeDependencies != null) {
            for (String qn : ownerType.methodBodyTypeDependencies) addRefByQName(ctx, referencedPackages, qn);
        }

        // Emit imports
        for (Package targetPkg : referencedPackages) {
            if (targetPkg == null) continue;
            if (targetPkg == ownerPkg) continue;
            String key = (qName(ownerPkg) + "|" + qName(targetPkg));
            if (ctx.packageImportPairs.contains(key)) continue;
            ctx.packageImportPairs.add(key);
            ownerPkg.createPackageImport(targetPkg);
            ctx.stats.packageImportsCreated++;
        }
    }

    private static void addRefByQName(UmlBuildContext ctx, Set<Package> pkgs, String qn) {
        if (ctx == null || pkgs == null) return;
        if (qn == null || qn.isBlank()) return;
        Classifier c = ctx.classifierByQName.get(qn);
        if (c == null) return;
        Package p = c.getPackage();
        if (p == null) p = c.getModel();
        if (p != null) pkgs.add(p);
    }

    private static void addRefByTypeRef(UmlBuildContext ctx, Set<Package> pkgs, TypeRef tr) {
        if (ctx == null || pkgs == null || tr == null) return;

        // direct hint
        if (tr.qnameHint != null && !tr.qnameHint.isBlank()) {
            addRefByQName(ctx, pkgs, tr.qnameHint);
        } else if (tr.raw != null && tr.raw.contains(".") && ctx.classifierByQName.containsKey(tr.raw)) {
            // last-resort: some raw strings are already qualified
            addRefByQName(ctx, pkgs, tr.raw);
        }

        // recurse into args / array component / wildcard bound
        if (tr.args != null) {
            for (TypeRef a : tr.args) addRefByTypeRef(ctx, pkgs, a);
        }
        if (tr.wildcardBoundType != null) {
            addRefByTypeRef(ctx, pkgs, tr.wildcardBoundType);
        }
    }

    private static String qName(Package p) {
        if (p == null) return "";
        String q = p.getQualifiedName();
        if (q == null) q = p.getName();
        return q == null ? "" : q;
    }
}

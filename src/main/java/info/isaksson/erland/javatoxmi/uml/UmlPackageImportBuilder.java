package info.isaksson.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Package;
import info.isaksson.erland.javatoxmi.model.JField;
import info.isaksson.erland.javatoxmi.model.JMethod;
import info.isaksson.erland.javatoxmi.model.JParam;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.TypeRef;
import info.isaksson.erland.javatoxmi.model.JAnnotationUse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        final String ownerJavaPkg = ownerType.packageName == null ? "" : ownerType.packageName;

        Package ownerPkg = ownerClassifier.getPackage();
        if (ownerPkg == null) ownerPkg = ownerClassifier.getModel();
        if (ownerPkg == null) return;

        // IMPORTANT: determinism.
        // Emitting imports using HashSet iteration order would make XMI nondeterministic
        // because Package instances have identity-based hash codes that vary across runs.
        // Collect by qualified name and emit in sorted order.
        // Keyed by Java package name for simple, deterministic sorting and easy suppression rules.
        Map<String, Package> referencedPackagesByJavaName = new HashMap<>();

        // extends/implements
        addRefByQName(ctx, referencedPackagesByJavaName, ownerType.extendsType);
        if (ownerType.implementsTypes != null) {
            for (String qn : ownerType.implementsTypes) addRefByQName(ctx, referencedPackagesByJavaName, qn);
        }

        // fields
        if (ownerType.fields != null) {
            for (JField f : ownerType.fields) {
                if (f == null) continue;
                addRefByTypeRef(ctx, referencedPackagesByJavaName, f.typeRef);
            }
        }

        // methods (signature)
        if (ownerType.methods != null) {
            for (JMethod m : ownerType.methods) {
                if (m == null) continue;
                addRefByTypeRef(ctx, referencedPackagesByJavaName, m.returnTypeRef);
                if (m.params != null) {
                    for (JParam p : m.params) {
                        if (p == null) continue;
                        addRefByTypeRef(ctx, referencedPackagesByJavaName, p.typeRef);
                    }
                }
            }
        }

        
        // annotations (types / members / params)
        addRefByAnnotationUses(ctx, referencedPackagesByJavaName, ownerType.annotations);
        if (ownerType.fields != null) {
            for (JField f : ownerType.fields) {
                if (f == null) continue;
                addRefByAnnotationUses(ctx, referencedPackagesByJavaName, f.annotations);
            }
        }
        if (ownerType.methods != null) {
            for (JMethod m : ownerType.methods) {
                if (m == null) continue;
                addRefByAnnotationUses(ctx, referencedPackagesByJavaName, m.annotations);
                if (m.params != null) {
                    for (JParam p : m.params) {
                        if (p == null) continue;
                        addRefByAnnotationUses(ctx, referencedPackagesByJavaName, p.annotations);
                    }
                }
            }
        }

// conservative method-body dependency hints (already resolved to qualified names when possible)
        if (ctx.includeDependencies && ownerType.methodBodyTypeDependencies != null) {
            for (String qn : ownerType.methodBodyTypeDependencies) addRefByQName(ctx, referencedPackagesByJavaName, qn);
        }

        // Emit imports
        List<String> sortedTargetPkgs = new ArrayList<>(referencedPackagesByJavaName.keySet());
        sortedTargetPkgs.sort(Comparator.naturalOrder());

        for (String targetJavaPkg : sortedTargetPkgs) {
            Package targetPkg = referencedPackagesByJavaName.get(targetJavaPkg);
            if (targetPkg == null) continue;
            if (targetPkg == ownerPkg) continue;

            // Default suppression policy (no CLI flag needed for now):
            // 1) suppress anything outside the analyzed source tree (external stubs like java.util, jakarta.*, etc.)
            //    This is handled by addRefByQName(..) only accepting qnames present in ctx.typeByQName.
            // 2) suppress imports to parent/ancestor packages (e.g. com.example.impl -> com.example)
            if (isAncestorPackage(targetJavaPkg, ownerJavaPkg)) continue;

            String key = (qName(ownerPkg) + "|" + qName(targetPkg));
            if (ctx.packageImportPairs.contains(key)) continue;
            ctx.packageImportPairs.add(key);
            ownerPkg.createPackageImport(targetPkg);
            ctx.stats.packageImportsCreated++;
        }
    }

    private static void addRefByQName(UmlBuildContext ctx, Map<String, Package> pkgsByJavaName, String qn) {
        if (ctx == null || pkgsByJavaName == null) return;
        if (qn == null || qn.isBlank()) return;

        // Suppress external references by default: only consider types that were part of the analyzed Java model.
        // This avoids creating/depending on packages like java.lang, java.util, jakarta.persistence, etc.
        if (!ctx.typeByQName.containsKey(qn)) return;

        // Derive Java package from qualified name, and map to (nested) UML Package.
        int lastDot = qn.lastIndexOf('.');
        if (lastDot <= 0) return;
        String pkgName = qn.substring(0, lastDot);

        Package p = new UmlClassifierBuilder().getOrCreatePackage(ctx, pkgName);
        if (p != null) pkgsByJavaName.putIfAbsent(pkgName, p);
    }

    
    private static void addRefByAnnotationUses(UmlBuildContext ctx, Map<String, Package> pkgsByJavaName, java.util.List<JAnnotationUse> anns) {
        if (ctx == null || pkgsByJavaName == null || anns == null) return;
        for (JAnnotationUse a : anns) {
            if (a == null) continue;
            if (a.qualifiedName == null || a.qualifiedName.isBlank()) continue;
            addRefByQName(ctx, pkgsByJavaName, a.qualifiedName);
        }
    }

    private static void addRefByTypeRef(UmlBuildContext ctx, Map<String, Package> pkgsByJavaName, TypeRef tr) {
        if (ctx == null || pkgsByJavaName == null || tr == null) return;

        // direct hint
        if (tr.qnameHint != null && !tr.qnameHint.isBlank()) {
            addRefByQName(ctx, pkgsByJavaName, tr.qnameHint);
        } else if (tr.raw != null && tr.raw.contains(".") && ctx.classifierByQName.containsKey(tr.raw)) {
            // last-resort: some raw strings are already qualified
            addRefByQName(ctx, pkgsByJavaName, tr.raw);
        }

        // recurse into args / array component / wildcard bound
        if (tr.args != null) {
            for (TypeRef a : tr.args) addRefByTypeRef(ctx, pkgsByJavaName, a);
        }
        if (tr.wildcardBoundType != null) {
            addRefByTypeRef(ctx, pkgsByJavaName, tr.wildcardBoundType);
        }
    }

    private static boolean isAncestorPackage(String candidateAncestor, String child) {
        if (candidateAncestor == null || candidateAncestor.isBlank()) return false;
        if (child == null || child.isBlank()) return false;
        if (candidateAncestor.equals(child)) return false;
        return child.startsWith(candidateAncestor + ".");
    }

    private static String qName(Package p) {
        if (p == null) return "";
        String q = p.getQualifiedName();
        if (q == null) q = p.getName();
        return q == null ? "" : q;
    }
}

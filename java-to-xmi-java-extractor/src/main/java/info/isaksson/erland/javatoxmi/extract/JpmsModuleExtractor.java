package info.isaksson.erland.javatoxmi.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import info.isaksson.erland.javatoxmi.model.JJavaModule;
import info.isaksson.erland.javatoxmi.model.JJavaModuleRequire;
import info.isaksson.erland.javatoxmi.model.JModel;

import java.util.*;

/**
 * Extracts Java Platform Module System (JPMS) metadata from {@code module-info.java}.
 */
public final class JpmsModuleExtractor {

    public void extract(JModel model, List<ParsedUnit> units) {
        if (model == null || units == null) return;

        Map<String, JJavaModule> byName = new LinkedHashMap<>();

        for (ParsedUnit u : units) {
            CompilationUnit cu = u.cu;
            Optional<ModuleDeclaration> modOpt = cu.getModule();
            if (modOpt.isEmpty()) continue;

            ModuleDeclaration md = modOpt.get();
            String moduleName = md.getNameAsString();
            if (moduleName == null || moduleName.isBlank()) continue;

            JJavaModule jm = byName.computeIfAbsent(moduleName, JJavaModule::new);

            for (ModuleDirective d : md.getDirectives()) {
                if (d instanceof ModuleExportsDirective ex) {
                    String pkg = ex.getNameAsString();
                    if (pkg != null && !pkg.isBlank()) jm.exports.add(pkg);
                } else if (d instanceof ModuleOpensDirective op) {
                    String pkg = op.getNameAsString();
                    if (pkg != null && !pkg.isBlank()) jm.opens.add(pkg);
                } else if (d instanceof ModuleRequiresDirective req) {
                    String reqName = req.getNameAsString();
                    if (reqName == null || reqName.isBlank()) continue;
                    boolean isStatic = req.isStatic();
                    boolean isTransitive = req.isTransitive();
                    jm.requires.add(new JJavaModuleRequire(reqName, isStatic, isTransitive));
                }
            }
        }

        // Determinism: sort inner lists and modules
        List<String> names = new ArrayList<>(byName.keySet());
        Collections.sort(names);

        for (String name : names) {
            JJavaModule jm = byName.get(name);
            jm.exports.sort(String::compareTo);
            jm.opens.sort(String::compareTo);
            jm.requires.sort(Comparator
                    .comparing((JJavaModuleRequire r) -> r.moduleName)
                    .thenComparing(r -> r.isStatic)
                    .thenComparing(r -> r.isTransitive));
            model.javaModules.add(jm);
        }
    }
}

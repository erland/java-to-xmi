package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.InterfaceRealization;
import se.erland.javatoxmi.model.JType;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds inheritance relationships (generalization + realization).
 */
final class UmlInheritanceBuilder {

    void addInheritanceAndRealization(UmlBuildContext ctx, Classifier classifier, JType t) {
        if (ctx == null || classifier == null || t == null) return;

        // extends
        if (t.extendsType != null && !t.extendsType.isBlank()) {
            Classifier superType = ctx.classifierByQName.get(t.extendsType);
            if (superType != null && classifier instanceof org.eclipse.uml2.uml.Class) {
                Generalization g = ((org.eclipse.uml2.uml.Class) classifier).createGeneralization(superType);
                ctx.stats.generalizationsCreated++;
                UmlBuilderSupport.annotateId(g, "Generalization:" + t.qualifiedName + "->" + t.extendsType);
            }
        }

        // implements
        if (t.implementsTypes != null) {
            List<String> impl = new ArrayList<>(t.implementsTypes);
            impl.sort(String::compareTo);
            for (String ifaceName : impl) {
                Classifier iface = ctx.classifierByQName.get(ifaceName);
                if (iface instanceof Interface && classifier instanceof org.eclipse.uml2.uml.Class) {
                    InterfaceRealization ir = ((org.eclipse.uml2.uml.Class) classifier)
                            .createInterfaceRealization("realizes_" + ((Interface) iface).getName(), (Interface) iface);
                    ctx.stats.interfaceRealizationsCreated++;
                    UmlBuilderSupport.annotateId(ir, "InterfaceRealization:" + t.qualifiedName + "->" + ifaceName);
                } else if (iface instanceof Interface && classifier instanceof Interface) {
                    Generalization g = ((Interface) classifier).createGeneralization(iface);
                    ctx.stats.generalizationsCreated++;
                    UmlBuilderSupport.annotateId(g, "InterfaceGeneralization:" + t.qualifiedName + "->" + ifaceName);
                }
            }
        }
    }
}

package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JJavaModule;
import info.isaksson.erland.javatoxmi.model.JJavaModuleRequire;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlJavaModuleEmitterTest {

    @Test
    void emitsModulesAsPackagesAndRequiresAsDependencies() {
        Model model = UMLFactory.eINSTANCE.createModel();
        model.setName("M");

        UmlBuildStats stats = new UmlBuildStats();
        UmlBuildContext ctx = new UmlBuildContext(
                model,
                stats,
                new MultiplicityResolver(),
                AssociationPolicy.RESOLVED,
                NestedTypesMode.UML,
                false,
                true,
                true
        );

        JJavaModule a = new JJavaModule("a.mod");
        a.exports.add("a.api");
        a.opens.add("a.internal");
        a.requires.add(new JJavaModuleRequire("b.mod", false, true));

        JJavaModule b = new JJavaModule("b.mod");
        b.exports.add("b.api");

        new UmlJavaModuleEmitter().emit(ctx, List.of(a, b));

        Package container = model.getNestedPackage("JavaModules");
        assertNotNull(container);

        Package pa = container.getNestedPackage("a.mod");
        Package pb = container.getNestedPackage("b.mod");
        assertNotNull(pa);
        assertNotNull(pb);

        assertNotNull(pa.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE));
        assertEquals("JavaModule", pa.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE).getDetails().get("stereotype"));

        // Requires dependency exists from a to b
        boolean found = false;
        for (Dependency d : pa.getClientDependencies()) {
            if (d.getSuppliers().contains(pb)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected requires dependency from a.mod to b.mod");
    }
}

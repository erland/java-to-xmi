package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.emitter.EmitterOptions;
import info.isaksson.erland.javatoxmi.emitter.XmiEmitter;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.ir.IrStereotypeDefinition;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 3: Verify that stereotype definitions in IR are materialized as UML Profiles + Stereotypes.
 *
 * <p>This does NOT test applying stereotypes to UML elements (Step 4).</p>
 */
public class IrStereotypeProfileBuilderTest {

    @Test
    public void materializesProfilesAndStereotypesFromIr() throws Exception {
        IrStereotypeDefinition def = new IrStereotypeDefinition(
                "st:frontend.Component",
                "Component",
                null,
                "Frontend",
                List.of("Class"),
                List.of()
        );

        IrModel ir = new IrModel(
                "1.0",
                List.of(def),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        EmitterOptions options = EmitterOptions.defaults("m").withStereotypes(true);

        XmiEmitter.Result result = new XmiEmitter().emit(ir, options, java.nio.file.Files.createTempFile("j2x", ".xmi"));

        Profile profile = null;
        for (PackageableElement pe : result.umlModel.getPackagedElements()) {
            if (pe instanceof Profile p && "Frontend".equals(p.getName())) {
                profile = p;
                break;
            }
        }
        assertNotNull(profile, "Expected UML Profile 'Frontend' to exist on the model.");

        Stereotype st = profile.getOwnedStereotype("Component");
        assertNotNull(st, "Expected stereotype 'Component' to exist in profile.");
    }
}

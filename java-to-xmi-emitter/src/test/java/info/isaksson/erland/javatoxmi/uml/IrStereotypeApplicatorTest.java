package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.emitter.EmitterOptions;
import info.isaksson.erland.javatoxmi.emitter.XmiEmitter;
import info.isaksson.erland.javatoxmi.ir.IrClassifier;
import info.isaksson.erland.javatoxmi.ir.IrClassifierKind;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.ir.IrStereotypeDefinition;
import info.isaksson.erland.javatoxmi.ir.IrStereotypeRef;
import info.isaksson.erland.javatoxmi.ir.IrSourceRef;
import info.isaksson.erland.javatoxmi.ir.IrVisibility;

import org.eclipse.uml2.uml.Class;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IrStereotypeApplicatorTest {

    @Test
    public void appliesStereotypeRefsToClassifier(@TempDir Path tmp) throws Exception {
        // Define a stereotype in IR registry (minimal)
        IrStereotypeDefinition def = new IrStereotypeDefinition(
                "st:frontend.Component",
                "Component",
                null,              // qualifiedName
                "Frontend",        // profileName
                List.of("Class"),  // appliesTo
                List.of()          // properties
        );

        // Classifier referencing that stereotype by id
        IrClassifier c = new IrClassifier(
                "c:AppComponent",
                "AppComponent",
                "AppComponent",
                null,
                IrClassifierKind.COMPONENT,
                IrVisibility.PUBLIC,
                List.of(),
                List.of(),
                List.of(),
                List.of(new IrStereotypeRef("st:frontend.Component", Map.of())),
                List.of(),
                new IrSourceRef("src/app/app.component.ts", 1, 1)
        );

        IrModel ir = new IrModel(
                "1.0",
                List.of(def),
                List.of(),
                List.of(c),
                List.of(),
                List.of()
        );

        Path outXmi = tmp.resolve("out.xmi");
        EmitterOptions opts = EmitterOptions.defaults("model").withStereotypes(true);

        XmiEmitter.Result res = new XmiEmitter().emit(ir, opts, outXmi);

        // Find the created UML class
        var pe = res.umlModel.getPackagedElement("AppComponent");
        assertNotNull(pe);
        assertTrue(pe instanceof Class);
        Class umlClass = (Class) pe;

        // Assert runtime marker was added; injector later turns this into stereotype applications in XMI
        var rt = umlClass.getEAnnotation("java-to-xmi:runtime");
        assertNotNull(rt);
        String stList = rt.getDetails().get("stereotype");
        assertNotNull(stList);
        assertTrue(java.util.Arrays.asList(stList.split(",")).contains("Component"));
    }
}

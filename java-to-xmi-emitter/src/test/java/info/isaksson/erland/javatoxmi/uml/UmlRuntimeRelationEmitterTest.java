package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.emitter.EmitterOptions;
import info.isaksson.erland.javatoxmi.emitter.XmiEmitter;
import info.isaksson.erland.javatoxmi.ir.*;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlRuntimeRelationEmitterTest {

    @Test
    void emitsRuntimeRelationAsStereotypedDependencyWithTags() throws Exception {
        IrClassifier a = new IrClassifier(
                "A",
                "Publisher",
                "com.acme.Publisher",
                null,
                IrClassifierKind.CLASS,
                IrVisibility.PUBLIC,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        IrClassifier b = new IrClassifier(
                "B",
                "EventType",
                "com.acme.EventType",
                null,
                IrClassifierKind.CLASS,
                IrVisibility.PUBLIC,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        IrRelation r = new IrRelation(
                "R1",
                IrRelationKind.DEPENDENCY,
                "A",
                "B",
                null,
                List.of(IrStereotype.simple(IrRuntime.ST_FIRES_EVENT)),
                List.of(new IrTaggedValue(IrRuntime.TAG_EVENT_QUALIFIERS, "@MyQualifier")),
                null
        );

        IrModel ir = new IrModel(
                "1.0",
                List.of(),
                List.of(a, b),
                List.of(r),
                List.of()
        );

        // Runtime stereotypes are emitted only when stereotypes are enabled.
        XmiEmitter.StringResult res = new XmiEmitter().emitToStringWithResult(
                ir,
                EmitterOptions.defaults("m").withStereotypes(true)
        );
        assertNotNull(res);
        assertNotNull(res.build);
        Model uml = res.build.umlModel;
        assertNotNull(uml);

        // Find dependency Publisher -> EventType
        Dependency dep = null;
        for (var pe : uml.getPackagedElements()) {
            // dependencies are usually owned by the client namespace; easiest is EMF traversal
        }
        // EMF traversal
        var it = uml.eAllContents();
        while (it.hasNext()) {
            var eo = it.next();
            if (eo instanceof Dependency d) {
                String client = d.getClients().isEmpty() ? null : d.getClients().get(0).getName();
                String supplier = d.getSuppliers().isEmpty() ? null : d.getSuppliers().get(0).getName();
                if ("Publisher".equals(client) && "EventType".equals(supplier)) {
                    dep = d;
                    break;
                }
            }
        }

        assertNotNull(dep, "Expected a dependency from Publisher to EventType");

        // Runtime stereotype marker should be present for post-processing.
        EAnnotation rt = dep.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE);
        assertNotNull(rt, "Expected java-to-xmi:runtime annotation on dependency");
        assertEquals(IrRuntime.ST_FIRES_EVENT, rt.getDetails().get(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_KEY));

        // And the final XMI should contain a stereotype application element.
        assertNotNull(res.xmi);
        assertTrue(res.xmi.contains("JavaAnnotations:FiresEvent"), "Expected FiresEvent stereotype application in emitted XMI");

        // NOTE: We rely on XMI post-processing (StereotypeApplicationInjector) for stereotypes,
        // since UML2 profile application can be brittle across tools/versions.
        // So we assert the runtime stereotype marker exists on the Dependency, and that the emitted XMI
        // contains a JavaAnnotations:FiresEvent stereotype application.

        // Tag should be present as java-to-xmi:tags annotation (later injected as J2XTags).
        EAnnotation tags = dep.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE);
        assertNotNull(tags, "Expected java-to-xmi:tags annotation on dependency");
        assertEquals("@MyQualifier", tags.getDetails().get(IrRuntime.TAG_EVENT_QUALIFIERS));
    }
}
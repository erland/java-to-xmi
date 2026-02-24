package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.model.JMethod;
import info.isaksson.erland.javatoxmi.model.JModel;
import info.isaksson.erland.javatoxmi.model.JParam;
import info.isaksson.erland.javatoxmi.model.JRuntimeAnnotation;
import info.isaksson.erland.javatoxmi.model.JType;
import info.isaksson.erland.javatoxmi.model.JTypeKind;
import info.isaksson.erland.javatoxmi.model.JVisibility;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Operation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UmlRuntimeAnnotationEmitterTest {

    @Test
    void annotatesClassAndOperationWithRuntimeMetadata() {
        // Minimal JModel with one class and one method.
        JModel jm = new JModel(Path.of("."), List.of());
        JMethod m = new JMethod("hello", "java.lang.String", JVisibility.PUBLIC, false, false, false, List.of());
        JType t = new JType(
                "com.example", "Api", "com.example.Api", null,
                JTypeKind.CLASS, JVisibility.PUBLIC,
                false, false, false,
                null, List.of(), List.of(),
                List.of(), List.of(m), List.of()
        );
        jm.types.add(t);

        Map<String, String> classTags = new LinkedHashMap<>();
        classTags.put("runtime.path", "/api");
        jm.runtimeAnnotations.add(new JRuntimeAnnotation("com.example.Api", "RestResource", classTags));

        Map<String, String> opTags = new LinkedHashMap<>();
        opTags.put("runtime.path", "/api/hello");
        opTags.put("runtime.httpMethod", "GET");
        jm.runtimeAnnotations.add(new JRuntimeAnnotation("com.example.Api#hello()", "RestOperation", opTags));

        UmlBuilder.Result res = new UmlBuilder().build(jm, "m", true);

        // The builder typically nests types inside packages (e.g., com::example),
        // so don't assume the classifier is a direct owned type of the root model.
        Classifier c = null;
        var it = res.umlModel.eAllContents();
        while (it.hasNext()) {
            Object eo = it.next();
            if (eo instanceof Classifier cc && "Api".equals(cc.getName())) {
                c = cc;
                break;
            }
        }
        assertNotNull(c);
        EAnnotation ca = c.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE);
        assertNotNull(ca);
        assertEquals("RestResource", ca.getDetails().get(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_KEY));
        assertEquals("/api", c.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE).getDetails().get("runtime.path"));

        Operation op = null;
        for (Operation o : c.getOperations()) {
            if ("hello".equals(o.getName())) { op = o; break; }
        }
        assertNotNull(op);
        EAnnotation oa = op.getEAnnotation(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_SOURCE);
        assertNotNull(oa);
        assertEquals("RestOperation", oa.getDetails().get(UmlBuilder.RUNTIME_STEREOTYPE_ANNOTATION_KEY));
        assertEquals("/api/hello", op.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE).getDetails().get("runtime.path"));
        assertEquals("GET", op.getEAnnotation(UmlBuilder.TAGS_ANNOTATION_SOURCE).getDetails().get("runtime.httpMethod"));
    }
}

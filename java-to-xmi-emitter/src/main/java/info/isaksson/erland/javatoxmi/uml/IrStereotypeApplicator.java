package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.ir.IrAttribute;
import info.isaksson.erland.javatoxmi.ir.IrClassifier;
import info.isaksson.erland.javatoxmi.ir.IrModel;
import info.isaksson.erland.javatoxmi.ir.IrOperation;
import info.isaksson.erland.javatoxmi.ir.IrRelation;
import info.isaksson.erland.javatoxmi.ir.IrStereotypeRef;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Stereotype;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies stereotypes to UML elements based on IR stereotypeRefs.
 *
 * <p>This uses Eclipse UML2's {@code applyStereotype} + {@code setValue} APIs.
 * The project already injects JavaAnnotations stereotypes via XMI post-processing;
 * IR-defined stereotypes are applied here so they are serialized by EMF in the base XMI.</p>
 *
 * <p>Determinism: refs and value keys are applied in sorted order.</p>
 */
public final class IrStereotypeApplicator {

    /** Annotation source used to link UML elements back to IR/JModel ids. */
    private static final String ID_SOURCE = UmlBuilder.ID_ANNOTATION_SOURCE;

    /** Annotation source used on stereotypes by IrStereotypeProfileBuilder. */
    private static final String IR_ST_META_SOURCE = IrStereotypeProfileBuilder.IR_STEREOTYPE_META_SOURCE;

    /** Detail key used on ID annotations (see UmlBuilderSupport.addAnnotationValue). */
    private static final String ANN_VALUE_KEY = "value";

    public void apply(org.eclipse.uml2.uml.Model umlModel, IrModel ir) {
        java.util.Map<String, String> stNameById = new java.util.HashMap<>();
        if (ir != null && ir.stereotypeDefinitions != null) {
            for (var def : ir.stereotypeDefinitions) {
                if (def != null && def.id != null && !def.id.isBlank() && def.name != null && !def.name.isBlank()) {
                    stNameById.put(def.id, def.name);
                }
            }
        }

        if (umlModel == null || ir == null) return;

        Map<String, Element> elementById = indexElementsById(umlModel);
        Map<String, Stereotype> stereotypeByIrId = indexIrStereotypes(umlModel);

        // Apply classifier stereotypes + nested attribute/operation stereotypes
        if (ir.classifiers != null) {
            for (IrClassifier c : ir.classifiers) {
                Element cEl = elementById.get(c.id);
                if (cEl == null && c.name != null && !c.name.isBlank()) {
                    var pe = umlModel.getPackagedElement(c.name);
                    if (pe instanceof Element) cEl = (Element) pe;
                }
                applyRefs(cEl, c.stereotypeRefs, stereotypeByIrId, stNameById);
                if (c.attributes != null) {
                    for (IrAttribute a : c.attributes) {
                        applyRefs(elementById.get(a.id), a.stereotypeRefs, stereotypeByIrId, stNameById);
                    }
                }
                if (c.operations != null) {
                    for (IrOperation o : c.operations) {
                        applyRefs(elementById.get(o.id), o.stereotypeRefs, stereotypeByIrId, stNameById);
                    }
                }
            }
        }

        // Apply relation stereotypes
        if (ir.relations != null) {
            for (IrRelation r : ir.relations) {
                applyRefs(elementById.get(r.id), r.stereotypeRefs, stereotypeByIrId, stNameById);
            }
        }
    }

    private static Map<String, Element> indexElementsById(org.eclipse.uml2.uml.Model umlModel) {
        Map<String, Element> map = new HashMap<>();
        TreeIterator<EObject> it = umlModel.eAllContents();
        while (it.hasNext()) {
            EObject eo = it.next();
            if (!(eo instanceof Element e)) continue;
            EAnnotation ann = e.getEAnnotation(ID_SOURCE);
            if (ann == null) continue;
            String id = ann.getDetails().get(ANN_VALUE_KEY);
            if (id == null || id.isBlank()) continue;
            map.put(id, e);
        }
        // include the model itself if annotated
        EAnnotation rootAnn = umlModel.getEAnnotation(ID_SOURCE);
        if (rootAnn != null) {
            String id = rootAnn.getDetails().get(ANN_VALUE_KEY);
            if (id != null && !id.isBlank()) map.put(id, umlModel);
        }
        return map;
    }

    private static Map<String, Stereotype> indexIrStereotypes(org.eclipse.uml2.uml.Model umlModel) {
        Map<String, Stereotype> map = new HashMap<>();
        TreeIterator<EObject> it = umlModel.eAllContents();
        while (it.hasNext()) {
            EObject eo = it.next();
            if (!(eo instanceof Stereotype st)) continue;
            EAnnotation ann = st.getEAnnotation(IR_ST_META_SOURCE);
            if (ann == null) continue;
            String id = ann.getDetails().get("id");
            if (id == null || id.isBlank()) continue;
            map.put(id, st);
        }
        return map;
    }

    private static void applyRefs(Element target, java.util.List<IrStereotypeRef> refs, java.util.Map<String, Stereotype> stereotypeByIrId, java.util.Map<String, String> stNameById) {
        if (target == null) return;
        if (refs == null || refs.isEmpty()) return;

        java.util.List<IrStereotypeRef> ordered = new java.util.ArrayList<>(refs);
        ordered.sort(java.util.Comparator.comparing(r -> r == null || r.stereotypeId == null ? "" : r.stereotypeId));

        java.util.List<String> runtimeNames = new java.util.ArrayList<>();
        for (IrStereotypeRef ref : ordered) {
            if (ref == null || ref.stereotypeId == null || ref.stereotypeId.isBlank()) continue;
            Stereotype st = stereotypeByIrId.get(ref.stereotypeId);
            if (st != null) {
                runtimeNames.add(st.getName());
            } else {
                String nm = stNameById == null ? null : stNameById.get(ref.stereotypeId);
                if (nm != null && !nm.isBlank()) {
                    runtimeNames.add(nm);
                }
            }
        }

        if (!runtimeNames.isEmpty()) {
            runtimeNames.sort(String::compareTo);
            // Store comma-separated list; StereotypeApplicationInjector supports this.
            UmlBuilderSupport.annotateRuntimeStereotype(target, String.join(",", runtimeNames));
        }
    }
}

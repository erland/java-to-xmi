package info.isaksson.erland.javatoxmi.uml;

import info.isaksson.erland.javatoxmi.ir.IrStereotypeDefinition;
import info.isaksson.erland.javatoxmi.ir.IrStereotypePropertyDefinition;

import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds UML Profiles + Stereotypes from IR stereotype definitions.
 *
 * <p>This enables IR producers to introduce new stereotypes without any code changes in the UML emitter.</p>
 *
 * <p>Note: applying stereotypes to elements (and setting tagged values) is handled elsewhere.</p>
 */
public final class IrStereotypeProfileBuilder {

    /** EAnnotation source for IR stereotype metadata (debugging + lookup aid). */
    static final String IR_STEREOTYPE_META_SOURCE = "java-to-xmi:ir-stereotype";

    public static final class Result {
        /** Map from IR stereotype id -> UML Stereotype */
        public final Map<String, Stereotype> stereotypeById;
        /** Map from profileName -> UML Profile */
        public final Map<String, Profile> profileByName;

        Result(Map<String, Stereotype> stereotypeById, Map<String, Profile> profileByName) {
            this.stereotypeById = stereotypeById;
            this.profileByName = profileByName;
        }
    }

    private final JavaAnnotationMetaclassExtensionHelper metaclassHelper = new JavaAnnotationMetaclassExtensionHelper();

    /**
     * Materialize UML Profiles/Stereotypes for the given IR definitions under the provided UML Model.
     *
     * @return a lookup map for later stereotype application.
     */
    public Result apply(Model model, List<IrStereotypeDefinition> definitions) {
        Objects.requireNonNull(model, "model");
        if (definitions == null || definitions.isEmpty()) {
            return new Result(Map.of(), Map.of());
        }

        // Deterministic ordering
        List<IrStereotypeDefinition> defs = new ArrayList<>(definitions);
        defs.sort(Comparator
                .comparing((IrStereotypeDefinition d) -> safe(d.profileName))
                .thenComparing(d -> safe(d.id))
                .thenComparing(d -> safe(d.name)));

        Map<String, Profile> profileByName = new HashMap<>();
        Map<String, Stereotype> stereotypeById = new HashMap<>();

        for (IrStereotypeDefinition def : defs) {
            if (def == null) continue;
            if (def.id == null || def.id.isBlank()) continue;
            if (def.name == null || def.name.isBlank()) continue;

            String profileName = (def.profileName == null || def.profileName.isBlank())
                    ? "IRProfile"
                    : def.profileName.trim();

            Profile profile = profileByName.computeIfAbsent(profileName, pn -> ensureProfile(model, pn));
            // Ensure profile is defined and applied to model (required for robust extension creation).
            ensureProfileDefinedAndApplied(model, profile);

            // Ensure stereotype exists (by name).
            Stereotype st = profile.getOwnedStereotype(def.name);
            if (st == null) {
                st = profile.createOwnedStereotype(def.name, false);
                UmlBuilderSupport.annotateId(st, "Stereotype:" + profileName + "#" + def.id);
            } else {
                UmlBuilderSupport.annotateId(st, "Stereotype:" + profileName + "#" + def.id);
            }

            // Store IR metadata on the stereotype (for debugging and stable matching).
            try {
                var ann = st.getEAnnotation(IR_STEREOTYPE_META_SOURCE);
                if (ann == null) ann = st.createEAnnotation(IR_STEREOTYPE_META_SOURCE);
                ann.getDetails().put("id", def.id);
                if (def.qualifiedName != null && !def.qualifiedName.isBlank()) {
                    ann.getDetails().put("qualifiedName", def.qualifiedName);
                }
                ann.getDetails().put("profileName", profileName);
            } catch (Throwable ignored) {
                // Best-effort.
            }

            // Extensions: ensure stereotype extends all requested metaclasses.
            if (def.appliesTo != null && !def.appliesTo.isEmpty()) {
                List<String> metaclasses = new ArrayList<>(def.appliesTo);
                metaclasses.removeIf(s -> s == null || s.isBlank());
                metaclasses.sort(String::compareTo);

                for (String mc : metaclasses) {
                    try {
                        metaclassHelper.ensureStereotypeExtendsMetaclass(profile, st, mc.trim(), false);
                    } catch (Throwable ignored) {
                        // Best-effort. We'll validate applicability when applying stereotypes.
                    }
                }
            } else {
                // If producer didn't specify appliesTo, be conservative: extend NamedElement.
                try {
                    metaclassHelper.ensureStereotypeExtendsMetaclass(profile, st, "NamedElement", false);
                } catch (Throwable ignored) {}
            }

            // Optional property schema -> stereotype owned attributes.
            if (def.properties != null && !def.properties.isEmpty()) {
                List<IrStereotypePropertyDefinition> props = new ArrayList<>(def.properties);
                props.removeIf(p -> p == null || p.name == null || p.name.isBlank());
                props.sort(Comparator.comparing(p -> p.name));

                for (IrStereotypePropertyDefinition p : props) {
                    ensureProperty(profile, st, p);
                }
            }

            stereotypeById.put(def.id, st);
        }

        // Ensure deterministic ids on any newly created elements.
        UmlBuilderSupport.ensureAllElementsHaveId(model);

        return new Result(stereotypeById, profileByName);
    }

    private static Profile ensureProfile(Model model, String profileName) {
        for (PackageableElement pe : model.getPackagedElements()) {
            if (pe instanceof Profile p && profileName.equals(p.getName())) {
                UmlBuilderSupport.annotateId(p, "Profile:" + profileName);
                return p;
            }
        }
        Profile p = UMLFactory.eINSTANCE.createProfile();
        p.setName(profileName);
        model.getPackagedElements().add(p);
        UmlBuilderSupport.annotateId(p, "Profile:" + profileName);
        return p;
    }

    private static void ensureProfileDefinedAndApplied(Model model, Profile profile) {
        if (model == null || profile == null) return;
        // define profile if needed
        try {
            java.lang.reflect.Method isDefined = profile.getClass().getMethod("isDefined");
            Object r = isDefined.invoke(profile);
            boolean defined = (r instanceof Boolean b) ? b : false;
            if (!defined) {
                java.lang.reflect.Method define = profile.getClass().getMethod("define");
                define.invoke(profile);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            // Best-effort.
        }

        // apply to model if not already applied
        try {
            boolean applied = false;
            for (Profile ap : model.getAppliedProfiles()) {
                if (ap == profile) {
                    applied = true;
                    break;
                }
                if (ap != null && Objects.equals(ap.getName(), profile.getName())) {
                    applied = true;
                    break;
                }
            }
            if (!applied) {
                model.applyProfile(profile);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureProperty(Profile profile, Stereotype st, IrStereotypePropertyDefinition def) {
        if (profile == null || st == null || def == null) return;
        String name = def.name.trim();

        for (Property existing : st.getOwnedAttributes()) {
            if (existing != null && name.equals(existing.getName())) {
                UmlBuilderSupport.annotateId(existing, "StereotypeAttr:" + st.getName() + "#" + name);
                return;
            }
        }

        PrimitiveType type = resolvePrimitive(profile, def.type);
        Property created = st.createOwnedAttribute(name, type);
        if (def.isMulti) {
            created.setUpper(-1);
        }
        UmlBuilderSupport.annotateId(created, "StereotypeAttr:" + st.getName() + "#" + name);
    }

    private static PrimitiveType resolvePrimitive(Profile profile, String raw) {
        String t = raw == null ? "string" : raw.trim().toLowerCase();
        String umlName;
        switch (t) {
            case "boolean" -> umlName = "Boolean";
            case "integer" -> umlName = "Integer";
            case "number" -> umlName = "Real";
            default -> umlName = "String";
        }
        return ensurePrimitiveType(profile, umlName);
    }

    private static PrimitiveType ensurePrimitiveType(Profile profile, String name) {
        if (profile == null) return null;

        // Prefer an existing global primitive in the enclosing model/package.
        org.eclipse.uml2.uml.Package searchRoot = profile.getModel();
        if (searchRoot != null) {
            PrimitiveType existing = findPrimitiveTypeByName(searchRoot, name);
            if (existing != null) {
                UmlBuilderSupport.annotateId(existing, "Primitive:" + name);
                return existing;
            }
        }

        // Create in model-level _primitives package.
        org.eclipse.uml2.uml.Package owner = (searchRoot != null) ? searchRoot : profile;
        org.eclipse.uml2.uml.Package primitivesPkg = findOrCreateChildPackage(owner, "_primitives");

        PrimitiveType pt = UMLFactory.eINSTANCE.createPrimitiveType();
        pt.setName(name);
        primitivesPkg.getPackagedElements().add(pt);
        UmlBuilderSupport.annotateId(pt, "Primitive:" + name);
        return pt;
    }

    private static PrimitiveType findPrimitiveTypeByName(org.eclipse.uml2.uml.Package root, String name) {
        if (root == null) return null;
        for (PackageableElement pe : root.getPackagedElements()) {
            if (pe instanceof PrimitiveType pt && name.equals(pt.getName())) return pt;
            if (pe instanceof org.eclipse.uml2.uml.Package p) {
                PrimitiveType nested = findPrimitiveTypeByName(p, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static org.eclipse.uml2.uml.Package findOrCreateChildPackage(org.eclipse.uml2.uml.Package owner, String name) {
        if (owner == null) return null;
        for (PackageableElement pe : owner.getPackagedElements()) {
            if (pe instanceof org.eclipse.uml2.uml.Package p && name.equals(p.getName())) {
                return p;
            }
        }
        org.eclipse.uml2.uml.Package p = UMLFactory.eINSTANCE.createPackage();
        p.setName(name);
        owner.getPackagedElements().add(p);
        UmlBuilderSupport.annotateId(p, "Package:" + name);
        return p;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

package se.erland.javatoxmi.uml;

import java.util.Objects;
import java.util.WeakHashMap;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.ElementImport;
import org.eclipse.uml2.uml.Extension;
import org.eclipse.uml2.uml.ExtensionEnd;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PackageImport;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;

/**
 * Handles UML metamodel referencing and robust stereotype→metaclass extension creation.
 *
 * <p>Eclipse UML2 3.1 is strict about metamodel identity; this helper ensures that the Profile
 * references the UML metamodel and uses a referenced metaclass instance when creating extensions.</p>
 */
final class JavaAnnotationMetaclassExtensionHelper {

    Extension ensureStereotypeExtendsMetaclass(Profile profile, Stereotype st, String metaclassName, boolean required) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(st, "st");
        Objects.requireNonNull(metaclassName, "metaclassName");

        org.eclipse.uml2.uml.Class metaclass = ensureMetaclassReference(profile, metaclassName);

        // Avoid duplicates
        Extension existing = findExistingExtension(profile, st, metaclass);
        if (existing != null) {
            JavaAnnotationProfileBuilder.annotateIdIfMissing(existing, "Extension:" + st.getName() + "->" + metaclass.getName());
            for (Property p : existing.getOwnedEnds()) {
                if (p == null) continue;
                JavaAnnotationProfileBuilder.annotateIdIfMissing(p,
                        "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
            }
            return existing;
        }

        Extension ext;
        try {
            ext = st.createExtension(metaclass, required);
        } catch (IllegalArgumentException ex) {
            ext = createExtensionManually(profile, st, metaclass, required);
        }

        JavaAnnotationProfileBuilder.annotateIdIfMissing(ext, "Extension:" + st.getName() + "->" + metaclass.getName());
        for (Property p : ext.getOwnedEnds()) {
            if (p == null) continue;
            JavaAnnotationProfileBuilder.annotateIdIfMissing(p,
                    "ExtensionEnd:" + st.getName() + "->" + metaclass.getName() + "#" + (p.getName() == null ? "" : p.getName()));
        }
        return ext;
    }

    private static Extension createExtensionManually(Profile profile, Stereotype st, org.eclipse.uml2.uml.Class metaclass, boolean required) {
        Extension ext = UMLFactory.eINSTANCE.createExtension();
        ext.setName(st.getName() + "_extends_" + metaclass.getName());

        ExtensionEnd baseEnd = UMLFactory.eINSTANCE.createExtensionEnd();
        baseEnd.setName("base_" + metaclass.getName());
        baseEnd.setType(metaclass);
        baseEnd.setLower(required ? 1 : 0);
        baseEnd.setUpper(1);
        baseEnd.setAggregation(AggregationKind.NONE_LITERAL);
        baseEnd.setAssociation(ext);

        ExtensionEnd stereoEnd = UMLFactory.eINSTANCE.createExtensionEnd();
        stereoEnd.setName(st.getName());
        stereoEnd.setType(st);
        stereoEnd.setLower(0);
        stereoEnd.setUpper(1);
        stereoEnd.setAggregation(AggregationKind.COMPOSITE_LITERAL);
        stereoEnd.setAssociation(ext);

        ext.getOwnedEnds().add(baseEnd);
        ext.getOwnedEnds().add(stereoEnd);

        profile.getPackagedElements().add(ext);
        return ext;
    }

    private static org.eclipse.uml2.uml.Class ensureMetaclassReference(Profile profile, String metaclassName) {
        ResourceSet rs = ensureResourceSetFor(profile);
        PackageableElement rawPe = UmlMetamodelCache.getMetaclass(rs, metaclassName);
        if (rawPe == null) {
            throw new IllegalStateException("Unable to locate UML metaclass '" + metaclassName + "'.");
        }
        if (!(rawPe instanceof org.eclipse.uml2.uml.Class)) {
            throw new IllegalStateException("UML metaclass '" + metaclassName + "' is not a UML Class element: " + rawPe.getClass().getName());
        }
        org.eclipse.uml2.uml.Class rawMetaclass = (org.eclipse.uml2.uml.Class) rawPe;

        org.eclipse.uml2.uml.Package rawPkg = rawMetaclass.getNearestPackage();
        if (rawPkg == null) {
            rawPkg = (org.eclipse.uml2.uml.Package) UmlMetamodelCache.getUmlMetamodel(rs);
        }

        ensureMetamodelReference(profile, rawPkg);

        ElementImport ei = findMetaclassImport(profile, metaclassName);
        if (ei == null) {
            try {
                Object created = profile.createMetaclassReference(rawMetaclass);
                if (created instanceof ElementImport) {
                    ei = (ElementImport) created;
                }
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to create metaclass reference for '" + metaclassName + "'.", t);
            }
        }
        if (ei == null) {
            ei = findMetaclassImport(profile, metaclassName);
        }
        if (ei != null) {
            JavaAnnotationProfileBuilder.annotateIdIfMissing(ei, "MetaclassRefImport:" + metaclassName);
        }

        ensureProfileDefined(profile);

        org.eclipse.uml2.uml.Class referenced = tryGetReferencedMetaclass(profile, metaclassName);
        if (referenced != null) {
            JavaAnnotationProfileBuilder.annotateIdIfMissing(referenced, "Metaclass:" + metaclassName);
            return referenced;
        }
        if (ei != null && ei.getImportedElement() instanceof org.eclipse.uml2.uml.Class c) {
            JavaAnnotationProfileBuilder.annotateIdIfMissing(c, "Metaclass:" + metaclassName);
            return c;
        }
        JavaAnnotationProfileBuilder.annotateIdIfMissing(rawMetaclass, "Metaclass:" + metaclassName);
        return rawMetaclass;
    }

    private static void ensureProfileDefined(Profile profile) {
        try {
            java.lang.reflect.Method isDefined = profile.getClass().getMethod("isDefined");
            Object r = isDefined.invoke(profile);
            boolean defined = (r instanceof Boolean b) ? b : false;
            if (!defined) {
                java.lang.reflect.Method define = profile.getClass().getMethod("define");
                define.invoke(profile);
                normalizeDefinedProfile(profile);
            }
        } catch (NoSuchMethodException nsme) {
            // Older/newer variants may not expose isDefined/define the same way.
        } catch (Throwable t) {
            throw new IllegalStateException("Profile.define() failed; cannot create metaclass extensions.", t);
        }
    }

    private static void normalizeDefinedProfile(Profile profile) {
        if (profile == null) return;
        final String desiredUri = JavaAnnotationProfileBuilder.PROFILE_URI;

        try {
            for (EAnnotation ea : profile.getEAnnotations()) {
                if (ea == null) continue;
                if (!"http://www.eclipse.org/uml2/2.0.0/UML".equals(ea.getSource())) continue;
                for (EObject c : ea.getContents()) {
                    if (c instanceof org.eclipse.emf.ecore.EPackage ep) {
                        if (!Objects.equals(ep.getNsURI(), desiredUri)) {
                            ep.setNsURI(desiredUri);
                        }
                        if (ep.getNsPrefix() == null || ep.getNsPrefix().isBlank()) {
                            ep.setNsPrefix(JavaAnnotationProfileBuilder.PROFILE_NAME);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Best-effort only.
        }
    }

    private static ElementImport findMetaclassImport(Profile profile, String metaclassName) {
        try {
            for (ElementImport ei : profile.getMetaclassReferences()) {
                if (ei == null) continue;
                if (ei.getImportedElement() instanceof org.eclipse.uml2.uml.Class c && metaclassName.equals(c.getName())) {
                    return ei;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static PackageImport ensureMetamodelReference(Profile profile, org.eclipse.uml2.uml.Package umlMetamodel) {
        if (umlMetamodel == null) return null;
        try {
            java.lang.reflect.Method getRefs = profile.getClass().getMethod("getMetamodelReferences");
            Object r = getRefs.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof PackageImport pi) {
                        if (pi.getImportedPackage() == umlMetamodel) return pi;
                        if (pi.getImportedPackage() != null && pi.getImportedPackage().eResource() != null && umlMetamodel.eResource() != null) {
                            if (Objects.equals(pi.getImportedPackage().eResource().getURI(), umlMetamodel.eResource().getURI())) return pi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method create = profile.getClass().getMethod("createMetamodelReference", org.eclipse.uml2.uml.Package.class);
            Object created = create.invoke(profile, umlMetamodel);
            if (created instanceof PackageImport pi) {
                JavaAnnotationProfileBuilder.annotateIdIfMissing(pi, "MetamodelRef:" + (umlMetamodel.getName() == null ? "UML" : umlMetamodel.getName()));
                return pi;
            } else if (created instanceof org.eclipse.uml2.uml.Element e) {
                JavaAnnotationProfileBuilder.annotateIdIfMissing(e, "MetamodelRef:" + (umlMetamodel.getName() == null ? "UML" : umlMetamodel.getName()));
            }
        } catch (Throwable ignored) {
            // Some UML2 variants may not require this.
        }

        try {
            java.lang.reflect.Method getRefs = profile.getClass().getMethod("getMetamodelReferences");
            Object r = getRefs.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof PackageImport pi) {
                        if (pi.getImportedPackage() == umlMetamodel) return pi;
                        if (pi.getImportedPackage() != null && pi.getImportedPackage().eResource() != null && umlMetamodel.eResource() != null) {
                            if (Objects.equals(pi.getImportedPackage().eResource().getURI(), umlMetamodel.eResource().getURI())) return pi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static org.eclipse.uml2.uml.Class tryGetReferencedMetaclass(Profile profile, String metaclassName) {
        try {
            java.lang.reflect.Method m = profile.getClass().getMethod("getReferencedMetaclasses");
            Object r = m.invoke(profile);
            if (r instanceof java.util.List<?> list) {
                for (Object o : list) {
                    if (o instanceof org.eclipse.uml2.uml.Class c && metaclassName.equals(c.getName())) {
                        return c;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ResourceSet ensureResourceSetFor(Element element) {
        Model m = (element instanceof Model) ? (Model) element : element.getModel();
        if (m != null && m.eResource() != null && m.eResource().getResourceSet() != null) {
            ResourceSet rs = m.eResource().getResourceSet();
            registerUmlResourceInfrastructure(rs);
            return rs;
        }

        ResourceSet rs = new ResourceSetImpl();
        registerUmlResourceInfrastructure(rs);
        Resource r = new XMIResourceImpl(URI.createURI("urn:java-to-xmi:model.uml"));
        rs.getResources().add(r);
        if (m != null) {
            r.getContents().add(m);
        }
        return rs;
    }

    private static void registerUmlResourceInfrastructure(ResourceSet rs) {
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);
        rs.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);

        try {
            Class<?> util = Class.forName("org.eclipse.uml2.uml.resources.util.UMLResourcesUtil");
            java.lang.reflect.Method init = util.getMethod("init", ResourceSet.class);
            init.invoke(null, rs);
        } catch (Throwable ignored) {
            // Safe to ignore.
        }
    }

    private static final class UmlMetamodelCache {
        private static final WeakHashMap<ResourceSet, Model> byResourceSet = new WeakHashMap<>();

        static Model getUmlMetamodel(ResourceSet rs) {
            synchronized (byResourceSet) {
                Model cached = byResourceSet.get(rs);
                if (cached != null) return cached;

                registerUmlResourceInfrastructure(rs);
                Resource r = tryLoadUmlMetamodel(rs);
                if (r == null || r.getContents().isEmpty()) {
                    throw new IllegalStateException("Failed to load UML metamodel resource.");
                }

                Object root = EcoreUtil.getObjectByType(r.getContents(), UMLPackage.Literals.MODEL);
                final Model m;
                if (root instanceof Model) {
                    m = (Model) root;
                } else {
                    Object first = r.getContents().get(0);
                    if (first instanceof Model) {
                        m = (Model) first;
                    } else {
                        throw new IllegalStateException("Unexpected UML metamodel root type: " + (first == null ? "null" : first.getClass().getName()));
                    }
                }
                byResourceSet.put(rs, m);
                return m;
            }
        }

        private static Resource tryLoadUmlMetamodel(ResourceSet rs) {
            try {
                return rs.getResource(URI.createURI(UMLResource.UML_METAMODEL_URI), true);
            } catch (Throwable ignored) {
                try {
                    ClassLoader cl = JavaAnnotationMetaclassExtensionHelper.class.getClassLoader();
                    String[] candidates = new String[] {"metamodels/UML.metamodel.uml", "UML.metamodel.uml"};
                    for (String p : candidates) {
                        java.net.URL url = cl.getResource(p);
                        if (url == null) continue;
                        URI uri = URI.createURI(url.toString());
                        return rs.getResource(uri, true);
                    }
                } catch (Throwable ignored2) {
                    // ignore
                }
                return null;
            }
        }

        static PackageableElement getMetaclass(ResourceSet rs, String metaclassName) {
            Model m = getUmlMetamodel(rs);
            for (PackageableElement pe : m.getPackagedElements()) {
                if (metaclassName.equals(pe.getName())) return pe;
            }
            for (PackageableElement pe : m.getPackagedElements()) {
                if (pe instanceof org.eclipse.uml2.uml.Package p) {
                    PackageableElement found = deepFind(p, metaclassName);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static PackageableElement deepFind(org.eclipse.uml2.uml.Package pkg, String name) {
            for (PackageableElement pe : pkg.getPackagedElements()) {
                if (name.equals(pe.getName())) return pe;
                if (pe instanceof org.eclipse.uml2.uml.Package p2) {
                    PackageableElement r = deepFind(p2, name);
                    if (r != null) return r;
                }
            }
            for (org.eclipse.uml2.uml.Package p : pkg.getNestedPackages()) {
                PackageableElement r = deepFind(p, name);
                if (r != null) return r;
            }
            return null;
        }
    }

    private static Extension findExistingExtension(Profile profile, Stereotype st, org.eclipse.uml2.uml.Class metaclass) {
        if (profile == null || st == null || metaclass == null) return null;

        for (PackageableElement pe : profile.getPackagedElements()) {
            if (!(pe instanceof Extension)) continue;
            Extension ext = (Extension) pe;
            if (extensionMatches(ext, st, metaclass)) return ext;
        }

        for (Extension ext : st.getExtensions()) {
            if (ext == null) continue;
            if (extensionMatches(ext, st, metaclass)) return ext;
        }
        return null;
    }

    private static boolean extensionMatches(Extension ext, Stereotype st, org.eclipse.uml2.uml.Class metaclass) {
        if (ext == null) return false;
        boolean hasStereoEnd = false;
        boolean hasMetaEnd = false;

        for (Property p : ext.getOwnedEnds()) {
            if (p == null || p.getType() == null) continue;
            if (p.getType() == st) hasStereoEnd = true;
            if (p.getType() == metaclass) hasMetaEnd = true;
        }

        if (!hasMetaEnd && metaclass.getName() != null) {
            for (Property p : ext.getOwnedEnds()) {
                if (p == null || p.getType() == null) continue;
                if (metaclass.getName().equals(p.getType().getName())) {
                    hasMetaEnd = true;
                    break;
                }
            }
        }

        return hasStereoEnd && hasMetaEnd;
    }
}

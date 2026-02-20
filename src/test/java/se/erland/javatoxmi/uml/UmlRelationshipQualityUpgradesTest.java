package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageImport;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.model.JAnnotationUse;
import se.erland.javatoxmi.model.JField;
import se.erland.javatoxmi.model.JModel;
import se.erland.javatoxmi.model.JType;
import se.erland.javatoxmi.model.JTypeKind;
import se.erland.javatoxmi.model.JVisibility;
import se.erland.javatoxmi.model.TypeRef;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UmlRelationshipQualityUpgradesTest {

    @Test
    void createsPackageImportsAndNamesOppositeEndFromMappedBy() {
        // p1.A has @OneToMany(mappedBy="owner") to p2.B
        JModel jm = new JModel(Path.of("."), List.of());

        JType b = new JType(
                "p2",
                "B",
                "p2.B",
                null,
                JTypeKind.CLASS,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                null,
                List.of(), // implements
                List.of(), // annotations
                "",       // doc
                List.of(), // fields
                List.of(), // methods
                List.of(), // enum literals
                List.of()  // method body deps
        );

        JAnnotationUse oneToMany = new JAnnotationUse(
                "OneToMany",
                "jakarta.persistence.OneToMany",
                Map.of("mappedBy", "\"owner\"")
        );
        JField rel = new JField(
                "bs",
                "java.util.List<p2.B>",
                TypeRef.param("List<p2.B>", "List", "java.util.List", List.of(TypeRef.simple("p2.B", "B", "p2.B"))),
                JVisibility.PRIVATE,
                false,
                false,
                List.of(oneToMany)
        );

        JType a = new JType(
                "p1",
                "A",
                "p1.A",
                null,
                JTypeKind.CLASS,
                JVisibility.PUBLIC,
                false,
                false,
                false,
                null,
                List.of(),      // implements
                List.of(),      // annotations
                "",            // doc
                List.of(rel),   // fields
                List.of(),      // methods
                List.of(),      // enum literals
                List.of()       // method body deps
        );

        jm.types.add(a);
        jm.types.add(b);

        UmlBuilder builder = new UmlBuilder();
        UmlBuilder.Result res = builder.build(jm, "T", false, AssociationPolicy.JPA_ONLY);
        Model model = res.umlModel;

        // Packages
        Package p1 = (Package) model.getPackagedElement("p1");
        Package p2 = (Package) model.getPackagedElement("p2");
        assertNotNull(p1);
        assertNotNull(p2);

        Classifier aC = (Classifier) p1.getOwnedType("A");
        assertNotNull(aC);

        // Package import p1 -> p2 should exist
        boolean hasImport = false;
        for (PackageImport pi : p1.getPackageImports()) {
            if (pi.getImportedPackage() == p2) {
                hasImport = true;
                break;
            }
        }
        assertTrue(hasImport, "Expected p1 to import p2");

        // Association opposite end should be named from mappedBy
        Association assoc = null;
        for (var pe : p1.getPackagedElements()) {
            if (pe instanceof Association aAssoc) {
                assoc = aAssoc;
                break;
            }
        }
        assertNotNull(assoc, "Expected an Association packaged in p1");

        Property opposite = null;
        for (Property p : assoc.getOwnedEnds()) {
            if (p.getType() == aC) {
                opposite = p;
                break;
            }
        }
        assertNotNull(opposite, "Expected association-owned opposite end typed by A");
        assertEquals("owner", opposite.getName());

        // Ensure opposite end is not navigable by default (unidirectional);
        // navigableOwnedEnds should be empty for this association.
        assertTrue(assoc.getNavigableOwnedEnds().isEmpty(), "Expected no association-owned navigable ends");
    }
}

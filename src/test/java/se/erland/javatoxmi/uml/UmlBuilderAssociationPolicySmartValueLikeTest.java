package se.erland.javatoxmi.uml;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Property;
import org.junit.jupiter.api.Test;
import se.erland.javatoxmi.extract.JavaExtractor;
import se.erland.javatoxmi.model.JModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UmlBuilderAssociationPolicySmartValueLikeTest {

    @Test
    void smart_skipsAssociationsForValueLikeTypes_evenIfTheyResolveInModel() throws Exception {
        Path root = Files.createTempDirectory("j2x-smart-valuetype");
        Path pkg = root.resolve("p");
        Files.createDirectories(pkg);

        // Define a local class named UUID to trigger the value-like blacklist by simple name.
        Path uuid = pkg.resolve("UUID.java");
        Files.writeString(uuid,
                "package p;\n" +
                "public class UUID { public String v; }\n",
                StandardCharsets.UTF_8);

        Path holder = pkg.resolve("Holder.java");
        Files.writeString(holder,
                "package p;\n" +
                "public class Holder { public UUID id; }\n",
                StandardCharsets.UTF_8);

        JModel jModel = new JavaExtractor().extract(root, List.of(uuid, holder));
        assertTrue(jModel.parseErrors.isEmpty(), "Parse errors: " + jModel.parseErrors);

        UmlBuilder.Result smartRes = new UmlBuilder().build(jModel, "SmartModel", true, AssociationPolicy.SMART);
        Model smartModel = smartRes.umlModel;

        Classifier holderC = findClassifierByName(smartModel, "Holder");
        Classifier uuidC = findClassifierByName(smartModel, "UUID");
        assertNotNull(holderC);
        assertNotNull(uuidC);

        assertFalse(hasAssociationBetween(smartModel, holderC, uuidC),
                "Did not expect Holder-UUID association under SMART because UUID is value-like by name");

        UmlBuilder.Result resolvedRes = new UmlBuilder().build(jModel, "ResolvedModel", true, AssociationPolicy.RESOLVED);
        Model resolvedModel = resolvedRes.umlModel;
        Classifier holderC2 = findClassifierByName(resolvedModel, "Holder");
        Classifier uuidC2 = findClassifierByName(resolvedModel, "UUID");
        assertNotNull(holderC2);
        assertNotNull(uuidC2);
        assertTrue(hasAssociationBetween(resolvedModel, holderC2, uuidC2),
                "Expected Holder-UUID association under RESOLVED because UUID resolves in model");
    }

    private static boolean hasAssociationBetween(Package pkg, Classifier a, Classifier b) {
        for (Association assoc : collectAssociations(pkg)) {
            List<Property> ends = assoc.getMemberEnds();
            if (ends.size() != 2) continue;
            var t1 = ends.get(0).getType();
            var t2 = ends.get(1).getType();
            if (t1 == a && t2 == b) return true;
            if (t1 == b && t2 == a) return true;
        }
        return false;
    }

    private static Classifier findClassifierByName(Package pkg, String name) {
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Classifier) {
                Classifier c = (Classifier) e;
                if (name.equals(c.getName())) return c;
            }
            if (e instanceof Package) {
                Classifier c = findClassifierByName((Package) e, name);
                if (c != null) return c;
            }
        }
        return null;
    }

    private static List<Association> collectAssociations(Package pkg) {
        List<Association> out = new ArrayList<>();
        for (Element e : pkg.getOwnedElements()) {
            if (e instanceof Association) out.add((Association) e);
            if (e instanceof Package) out.addAll(collectAssociations((Package) e));
        }
        return out;
    }
}

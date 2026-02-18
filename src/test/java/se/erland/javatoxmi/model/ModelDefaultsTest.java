package se.erland.javatoxmi.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelDefaultsTest {

    @Test
    void legacyConstructors_defaultNewFields() {
        JField f = new JField("a", "String", JVisibility.PRIVATE, false, false);
        assertNull(f.typeRef);
        assertNotNull(f.annotations);
        assertTrue(f.annotations.isEmpty());

        JParam p = new JParam("x", "int");
        assertNull(p.typeRef);
        assertNotNull(p.annotations);
        assertTrue(p.annotations.isEmpty());

        JMethod m = new JMethod("doIt", "void", JVisibility.PUBLIC, false, false, false, List.of(p));
        assertNull(m.returnTypeRef);
        assertNotNull(m.annotations);
        assertTrue(m.annotations.isEmpty());
    }
}

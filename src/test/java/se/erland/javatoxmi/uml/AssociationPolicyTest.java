package se.erland.javatoxmi.uml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AssociationPolicyTest {

    @Test
    void parseCli_defaultsToResolved() {
        assertEquals(AssociationPolicy.RESOLVED, AssociationPolicy.parseCli(null));
    }

    @Test
    void parseCli_acceptsKnownValues() {
        assertEquals(AssociationPolicy.NONE, AssociationPolicy.parseCli("none"));
        assertEquals(AssociationPolicy.JPA_ONLY, AssociationPolicy.parseCli("jpa"));
        assertEquals(AssociationPolicy.RESOLVED, AssociationPolicy.parseCli("resolved"));
        assertEquals(AssociationPolicy.SMART, AssociationPolicy.parseCli("smart"));
    }

    @Test
    void parseCli_rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> AssociationPolicy.parseCli("nope"));
    }
}

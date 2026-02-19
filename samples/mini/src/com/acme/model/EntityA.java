package com.acme.model;

import com.acme.jpa.CascadeType;
import com.acme.jpa.FetchType;
import com.acme.jpa.ManyToOne;

/**
 * Demonstrates JPA property-access style relationship annotations (annotations on getters).
 *
 * The generator propagates relationship annotations from getters to the backing field
 * so association generation works even when you use property access in your entities.
 */
public class EntityA {
    private EntityB attrB;

    // Intentionally annotate the getter (property access), not the field.
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public EntityB getAttrB() {
        return attrB;
    }
}

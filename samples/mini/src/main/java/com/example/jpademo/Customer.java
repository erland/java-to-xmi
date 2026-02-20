package com.example.jpademo;

import com.example.jpademo.annotations.ElementCollection;
import com.example.jpademo.annotations.Embedded;
import com.example.jpademo.annotations.OneToMany;
import com.example.jpademo.annotations.Transient;
import com.example.jpademo.validation.NotNull;
import com.example.jpademo.validation.Size;

import java.util.List;

public class Customer {

    // Relationship: should become an association, and with orphanRemoval=true it can be treated as composition.
    @OneToMany(orphanRemoval = true)
    @NotNull
    @Size(min = 1)
    private List<Order> orders;

    // Attribute-only containment: should NOT become an association line.
    @Embedded
    private Address address;

    // Attribute-only element collection: should NOT become an association line.
    @ElementCollection
    private List<Address> previousAddresses;

    @ElementCollection
    private List<String> tags;

    // Non-persistent helper field: should not become an association line.
    @Transient
    private Address runtimeOnly;
}

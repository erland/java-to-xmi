package com.acme.model;

import com.acme.annotations.Audited;
import com.acme.annotations.Entity;

@Entity("customer")
@Audited(level = "HIGH", enabled = true)
public class Customer {
    private final String id;
    private String name;

    public Customer(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

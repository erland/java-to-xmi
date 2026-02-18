package com.example.jpademo;

import com.example.jpademo.annotations.ManyToOne;
import com.example.jpademo.annotations.JoinColumn;
import com.example.jpademo.validation.NotNull;

public class Order {

    // Relationship: should become an association; lower bound should be 1 due to NotNull / nullable=false.
    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    @NotNull
    private Customer customer;
}

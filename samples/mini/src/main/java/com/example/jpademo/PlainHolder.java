package com.example.jpademo;

/**
 * Used by tests to verify that --associations=jpa does not create associations
 * for plain resolved field types without JPA relationship annotations.
 */
public class PlainHolder {
    private Order order;
}

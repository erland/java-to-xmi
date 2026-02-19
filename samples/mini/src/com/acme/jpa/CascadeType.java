package com.acme.jpa;

/**
 * Minimal stub enum used by samples/mini so the parser can read
 * JPA-like annotations without pulling in real JPA dependencies.
 */
public enum CascadeType {
    ALL,
    PERSIST,
    MERGE,
    REMOVE,
    REFRESH,
    DETACH
}

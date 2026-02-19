package com.acme.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimal stub annotation used by samples/mini.
 *
 * Note: the generator's heuristics intentionally match relationship annotations
 * by both simple name (e.g. "ManyToOne") and the common qualified names
 * (javax/jakarta.persistence.*). This sample uses a local annotation with the
 * same simple name to keep the sample dependency-free.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ManyToOne {
    CascadeType[] cascade() default {};
    FetchType fetch() default FetchType.EAGER;
}

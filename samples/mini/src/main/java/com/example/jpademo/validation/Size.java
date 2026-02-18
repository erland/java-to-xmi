package com.example.jpademo.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface Size {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
}

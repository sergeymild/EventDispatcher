package com.sergeymild.event_dispatcher;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Subscribe {
    String value();
    boolean once() default false;
}

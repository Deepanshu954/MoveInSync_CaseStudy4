package com.moveinsync.settlement.common.annotation;

import java.lang.annotation.*;

/**
 * Performance metric annotation logging method execution latency in milliseconds.
 *
 * @author Deepanshu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExecutionTime {

    /**
     * Human-readable label for log statements. Defaults to method signature.
     */
    String value() default "";
}

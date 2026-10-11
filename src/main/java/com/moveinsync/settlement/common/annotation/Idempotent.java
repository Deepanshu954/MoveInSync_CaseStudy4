package com.moveinsync.settlement.common.annotation;

import java.lang.annotation.*;

/**
 * Declarative annotation guaranteeing idempotency for mutating financial operations.
 *
 * <p>When placed on a Spring MVC controller endpoint:
 * <ul>
 *   <li>Intercepts incoming requests and reads the idempotency key (default: {@code Idempotency-Key}).</li>
 *   <li>Computes SHA-256 payload checksums to prevent request tampering.</li>
 *   <li>Replays cached completed responses without re-executing business logic.</li>
 *   <li>Rejects concurrent in-flight executions with HTTP 409 Conflict.</li>
 * </ul>
 *
 * @author Deepanshu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * Name of the HTTP request header containing the client idempotency token.
     */
    String headerName() default "Idempotency-Key";

    /**
     * Time-to-live for the cached response in hours.
     */
    int ttlHours() default 24;

    /**
     * Whether to ignore preview-mode calls (e.g. previewOnly=true in payload).
     */
    boolean ignorePreview() default true;
}

package com.moveinsync.settlement.common.annotation;

import java.lang.annotation.*;

/**
 * Declarative audit logging annotation for sensitive financial operations.
 *
 * <p>Persists immutable records into {@code audit_event} table including the authenticated actor,
 * action, target entity type, entity ID, and correlation token.
 *
 * @author MoveInSync Core Settlement Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /**
     * Target domain entity classification (e.g. "SETTLEMENT_RUN", "PAYOUT", "DISPUTE").
     */
    String entityType() default "SYSTEM";

    /**
     * Business action identifier (e.g. "CALCULATE", "APPROVE", "DISBURSE").
     */
    String action();
}

package com.moveinsync.settlement.payout.handler;

import com.moveinsync.settlement.entity.OutboxEvent;

/**
 * Strategy interface for processing asynchronous outbox events.
 *
 * <p>Adheres to the Open/Closed Principle: new events can be added without modifying
 * the core dispatcher loop.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface OutboxEventHandler {

    /**
     * Determines if this handler supports the specified outbox event type.
     */
    boolean supports(String eventType);

    /**
     * Dispatches the outbox event payload to external systems or downstream queues.
     *
     * @param event the outbox event record
     * @return true if processing succeeded
     * @throws Exception if a dispatch error occurs
     */
    boolean handle(OutboxEvent event) throws Exception;
}

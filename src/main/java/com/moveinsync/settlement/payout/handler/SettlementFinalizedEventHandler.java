package com.moveinsync.settlement.payout.handler;

import com.moveinsync.settlement.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles 'SETTLEMENT_FINALIZED' aggregate notifications.
 */
@Component
@Slf4j
public class SettlementFinalizedEventHandler implements OutboxEventHandler {

    @Override
    public boolean supports(String eventType) {
        return "SETTLEMENT_FINALIZED".equals(eventType);
    }

    @Override
    public boolean handle(OutboxEvent event) {
        log.info("Settlement run finalized notification published: aggregateId={}", event.getAggregateId());
        event.markProcessed();
        return true;
    }
}

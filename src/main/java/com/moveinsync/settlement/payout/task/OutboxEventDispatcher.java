package com.moveinsync.settlement.payout.task;

import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.payout.handler.OutboxEventHandler;
import com.moveinsync.settlement.payout.handler.PaymentDispatchException;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Dispatches individual outbox events in separate transactions so failures in one event
 * do not block or roll back other pending events. Handles retries and failure status updates.
 *
 * @author Deepanshu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final PayoutInstructionRepository payoutInstructionRepository;
    private final List<OutboxEventHandler> eventHandlers;

    public static final int MAX_RETRIES = 3;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatchSingleEvent(OutboxEvent event) {
        try {
            OutboxEventHandler handler = eventHandlers.stream()
                    .filter(h -> h.supports(event.getEventType()))
                    .findFirst()
                    .orElse(null);

            if (handler == null) {
                log.warn("Unrecognized outbox event type: {}, marking processed", event.getEventType());
                event.markProcessed();
                outboxEventRepository.save(event);
                return true;
            }

            boolean success = handler.handle(event);
            if (success) {
                outboxEventRepository.save(event);
                return true;
            } else {
                handleFailure(event, null, "Handler returned false without exception");
                return false;
            }
        } catch (PaymentDispatchException pde) {
            log.warn("Payment dispatch failure for outbox event {}: {}", event.getId(), pde.getMessage());
            handleFailure(event, pde.getPayout(), pde.getMessage());
            return false;
        } catch (Exception ex) {
            log.error("Error dispatching outbox event {}: {}", event.getId(), ex.getMessage(), ex);
            handleFailure(event, null, "DISPATCH_ERROR: " + ex.getMessage());
            return false;
        }
    }

    private void handleFailure(OutboxEvent event, PayoutInstruction payout, String reason) {
        int retries = event.getRetryCount() + 1;
        event.setRetryCount(retries);

        if (retries >= MAX_RETRIES) {
            log.error("DEAD-LETTER ALERT: Outbox event {} reached max retries ({}), marking FAILED. Reason: {}",
                    event.getId(), MAX_RETRIES, reason);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());

            if (payout != null) {
                payout.markFailed(reason);
                payoutInstructionRepository.save(payout);
            }
        } else {
            log.warn("Transient failure for outbox event {} (attempt {}/{}): {}",
                    event.getId(), retries, MAX_RETRIES, reason);
        }

        outboxEventRepository.save(event);
    }
}

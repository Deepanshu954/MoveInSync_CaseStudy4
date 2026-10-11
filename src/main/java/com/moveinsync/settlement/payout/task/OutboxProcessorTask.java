package com.moveinsync.settlement.payout.task;

import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.dto.OutboxProcessResultDto;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.common.metrics.SettlementMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Horizontally-scalable Transactional Outbox worker.
 *
 * <p>Uses PostgreSQL {@code SKIP LOCKED} to prevent multi-instance race conditions and
 * coordinates with {@link OutboxEventDispatcher} for per-event transaction isolation.
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxProcessorTask {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher outboxEventDispatcher;
    private final SettlementMetrics settlementMetrics;

    @Scheduled(fixedDelayString = "${settlement.outbox.poll-interval-ms:5000}")
    public void scheduledProcess() {
        processPendingEvents();
    }

    /**
     * Executes pending outbox events batch using PostgreSQL SKIP LOCKED.
     */
    @ExecutionTime
    public OutboxProcessResultDto processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForProcessing(50);

        if (pendingEvents.isEmpty()) {
            return OutboxProcessResultDto.builder()
                    .totalEvaluated(0)
                    .processedCount(0)
                    .failedCount(0)
                    .pendingRemaining(0)
                    .build();
        }

        log.info("Claimed {} pending Outbox events via SKIP LOCKED", pendingEvents.size());
        int processed = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            boolean success = outboxEventDispatcher.dispatchSingleEvent(event);
            if (success) {
                processed++;
            } else {
                failed++;
            }
        }

        if (processed > 0) settlementMetrics.incrementOutboxDispatched(processed);
        if (failed > 0) settlementMetrics.incrementOutboxDlq(failed);
        long remaining = outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING);
        log.info("Outbox batch completed: evaluated={}, processed={}, failed={}, remainingPending={}",
                pendingEvents.size(), processed, failed, remaining);

        return OutboxProcessResultDto.builder()
                .totalEvaluated(pendingEvents.size())
                .processedCount(processed)
                .failedCount(failed)
                .pendingRemaining(remaining)
                .build();
    }

    /**
     * Manually retries a specific dead-lettered event from the DLQ.
     */
    public boolean retrySingleEvent(Long eventId) {
        OutboxEvent event = outboxEventRepository
                .findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        event.requeue();
        outboxEventRepository.save(event);
        return outboxEventDispatcher
                .dispatchSingleEvent(event);
    }

    /**
     * Retries all failed events currently in the DLQ.
     */
    public int retryAllFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.FAILED
        );
        for (OutboxEvent event : failedEvents) {
            event.requeue();
        }
        outboxEventRepository.saveAll(failedEvents);
        return failedEvents.size();
    }
}

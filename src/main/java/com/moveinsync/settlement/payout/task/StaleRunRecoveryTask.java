package com.moveinsync.settlement.payout.task;

import com.moveinsync.settlement.entity.AuditEvent;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.repository.AuditEventRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Automated crash recovery background task resolving orphaned settlement runs.
 *
 * <p>Identifies runs abandoned in {@code PROCESSING} status due to JVM termination,
 * container crash, or node failure, and transitions them to {@code FAILED} to unblock
 * subsequent billing runs under the partial unique index.
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StaleRunRecoveryTask {

    private final SettlementRunRepository settlementRunRepository;
    private final AuditEventRepository auditEventRepository;

    @Value("${settlement.stale-run.cutoff-minutes:30}")
    private long cutoffMinutes;

    @Scheduled(
            fixedDelayString = "${settlement.stale-run.check-interval-ms:900000}",
            initialDelayString = "${settlement.stale-run.initial-delay-ms:60000}"
    )
    @Transactional
    public int recoverStaleRuns() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(cutoffMinutes));
        List<SettlementRun> staleRuns = settlementRunRepository.findStaleProcessingRuns(cutoff);

        if (staleRuns.isEmpty()) {
            return 0;
        }

        log.warn("Found {} stale settlement run(s) stuck in PROCESSING status past cutoff {}",
                staleRuns.size(), cutoff);

        int recoveredCount = 0;
        for (SettlementRun run : staleRuns) {
            log.warn("Recovering stale run ID: {} (vendorId: {}, period: {} to {}) to FAILED",
                    run.getId(), run.getVendor().getId(), run.getPeriodStart(), run.getPeriodEnd());

            run.setStatus(SettlementRun.RunStatus.FAILED);
            run.setUpdatedAt(Instant.now());
            settlementRunRepository.save(run);

            AuditEvent audit = AuditEvent.builder()
                    .actor("SYSTEM_RECOVERY")
                    .action("STALE_RUN_RECOVERED_TO_FAILED")
                    .entityType("SettlementRun")
                    .entityId(run.getId())
                    .correlationId(MDC.get("traceId"))
                    .details(String.format("{\"previousStatus\":\"PROCESSING\",\"recoveredAt\":\"%s\",\"cutoffMinutes\":%d}",
                            Instant.now(), cutoffMinutes))
                    .createdAt(Instant.now())
                    .build();
            auditEventRepository.save(audit);
            recoveredCount++;
        }

        log.info("Successfully recovered {} stale settlement run(s) to FAILED", recoveredCount);
        return recoveredCount;
    }
}

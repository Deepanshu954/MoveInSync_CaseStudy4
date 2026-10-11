package com.moveinsync.settlement.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Enterprise Micrometer metrics service exposing key business counters.
 *
 * <p>Tracks operational volume, throughput, and error rates across settlement runs,
 * outbox dispatch, tolerance reconciliations, and dispute resolutions.
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
public class SettlementMetrics {

    private final Counter runsCalculated;
    private final Counter runsPreviewed;
    private final Counter idempotentHits;
    private final Counter tripsProcessed;
    private final Counter driversProcessed;
    private final Counter disputesRaised;
    private final Counter disputesResolved;
    private final Counter disputesRejected;
    private final Counter reconciliationsMatched;
    private final Counter reconciliationsDiscrepancy;
    private final Counter payoutsApproved;
    private final Counter payoutsFinalized;
    private final Counter outboxDispatched;
    private final Counter outboxDlq;

    public SettlementMetrics(MeterRegistry registry) {
        this.runsCalculated = Counter.builder("settlement.runs.calculated")
                .description("Total persisted settlement calculation runs")
                .register(registry);

        this.runsPreviewed = Counter.builder("settlement.runs.previewed")
                .description("Total preview settlement simulations")
                .register(registry);

        this.idempotentHits = Counter.builder("settlement.runs.idempotent_hit")
                .description("Total duplicate requests served from idempotency cache")
                .register(registry);

        this.tripsProcessed = Counter.builder("settlement.trips.processed")
                .description("Total individual transport trips processed")
                .register(registry);

        this.driversProcessed = Counter.builder("settlement.drivers.processed")
                .description("Total driver payslips generated")
                .register(registry);

        this.disputesRaised = Counter.builder("settlement.disputes.raised")
                .description("Total driver trip disputes opened")
                .register(registry);

        this.disputesResolved = Counter.builder("settlement.disputes.resolved")
                .description("Total disputes approved with delta settlement")
                .register(registry);

        this.disputesRejected = Counter.builder("settlement.disputes.rejected")
                .description("Total disputes dismissed without adjustment")
                .register(registry);

        this.reconciliationsMatched = Counter.builder("settlement.reconciliations.matched")
                .description("Total vendor invoices matched within tolerance")
                .register(registry);

        this.reconciliationsDiscrepancy = Counter.builder("settlement.reconciliations.discrepancy")
                .description("Total vendor invoices flagged with monetary discrepancy")
                .register(registry);

        this.payoutsApproved = Counter.builder("settlement.payouts.approved")
                .description("Total payout runs approved by finance")
                .register(registry);

        this.payoutsFinalized = Counter.builder("settlement.payouts.finalized")
                .description("Total payout runs finalized for disbursement")
                .register(registry);

        this.outboxDispatched = Counter.builder("settlement.outbox.dispatched")
                .description("Total outbox banking events dispatched")
                .register(registry);

        this.outboxDlq = Counter.builder("settlement.outbox.dlq")
                .description("Total exhausted outbox events moved to dead-letter queue")
                .register(registry);
    }

    public void incrementRunsCalculated() { runsCalculated.increment(); }
    public void incrementRunsPreviewed() { runsPreviewed.increment(); }
    public void incrementIdempotentHit() { idempotentHits.increment(); }
    public void incrementTripsProcessed(long count) { tripsProcessed.increment(count); }
    public void incrementDriversProcessed(long count) { driversProcessed.increment(count); }
    public void incrementDisputesRaised() { disputesRaised.increment(); }
    public void incrementDisputesResolved() { disputesResolved.increment(); }
    public void incrementDisputesRejected() { disputesRejected.increment(); }
    public void incrementReconciliationsMatched() { reconciliationsMatched.increment(); }
    public void incrementReconciliationsDiscrepancy() { reconciliationsDiscrepancy.increment(); }
    public void incrementPayoutsApproved() { payoutsApproved.increment(); }
    public void incrementPayoutsFinalized() { payoutsFinalized.increment(); }
    public void incrementOutboxDispatched(long count) { outboxDispatched.increment(count); }
    public void incrementOutboxDlq(long count) { outboxDlq.increment(count); }

    public double getRunsCalculatedCount() { return runsCalculated.count(); }
    public double getRunsPreviewedCount() { return runsPreviewed.count(); }
    public double getIdempotentHitsCount() { return idempotentHits.count(); }
    public double getTripsProcessedCount() { return tripsProcessed.count(); }
    public double getDriversProcessedCount() { return driversProcessed.count(); }
    public double getDisputesRaisedCount() { return disputesRaised.count(); }
    public double getDisputesResolvedCount() { return disputesResolved.count(); }
    public double getDisputesRejectedCount() { return disputesRejected.count(); }
    public double getReconciliationsMatchedCount() { return reconciliationsMatched.count(); }
    public double getReconciliationsDiscrepancyCount() { return reconciliationsDiscrepancy.count(); }
    public double getPayoutsApprovedCount() { return payoutsApproved.count(); }
    public double getPayoutsFinalizedCount() { return payoutsFinalized.count(); }
    public double getOutboxDispatchedCount() { return outboxDispatched.count(); }
    public double getOutboxDlqCount() { return outboxDlq.count(); }
}

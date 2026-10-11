package com.moveinsync.settlement.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettlementMetrics Micrometer Observability Tests")
class SettlementMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private SettlementMetrics settlementMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        settlementMetrics = new SettlementMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should correctly record and increment operational settlement counters")
    void shouldIncrementSettlementCounters() {
        settlementMetrics.incrementRunsCalculated();
        settlementMetrics.incrementRunsPreviewed();
        settlementMetrics.incrementIdempotentHit();
        settlementMetrics.incrementTripsProcessed(180);
        settlementMetrics.incrementDriversProcessed(2);
        settlementMetrics.incrementDisputesRaised();
        settlementMetrics.incrementDisputesResolved();
        settlementMetrics.incrementDisputesRejected();
        settlementMetrics.incrementReconciliationsMatched();
        settlementMetrics.incrementReconciliationsDiscrepancy();
        settlementMetrics.incrementPayoutsApproved();
        settlementMetrics.incrementPayoutsFinalized();
        settlementMetrics.incrementOutboxDispatched(5);
        settlementMetrics.incrementOutboxDlq(1);

        assertThat(settlementMetrics.getRunsCalculatedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getRunsPreviewedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getIdempotentHitsCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getTripsProcessedCount()).isEqualTo(180.0);
        assertThat(settlementMetrics.getDriversProcessedCount()).isEqualTo(2.0);
        assertThat(settlementMetrics.getDisputesRaisedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getDisputesResolvedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getDisputesRejectedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getReconciliationsMatchedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getReconciliationsDiscrepancyCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getPayoutsApprovedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getPayoutsFinalizedCount()).isEqualTo(1.0);
        assertThat(settlementMetrics.getOutboxDispatchedCount()).isEqualTo(5.0);
        assertThat(settlementMetrics.getOutboxDlqCount()).isEqualTo(1.0);

        // Verify registration in MeterRegistry
        assertThat(meterRegistry.find("settlement.runs.calculated").counter()).isNotNull();
        assertThat(meterRegistry.find("settlement.trips.processed").counter().count()).isEqualTo(180.0);
    }
}

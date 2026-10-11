package com.moveinsync.settlement.payout.task;

import com.moveinsync.settlement.entity.AuditEvent;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.repository.AuditEventRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaleRunRecoveryTask Crash Recovery Tests")
class StaleRunRecoveryTaskTest {

    @Mock
    private SettlementRunRepository settlementRunRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    private StaleRunRecoveryTask staleRunRecoveryTask;

    @BeforeEach
    void setUp() {
        staleRunRecoveryTask = new StaleRunRecoveryTask(settlementRunRepository, auditEventRepository);
        ReflectionTestUtils.setField(staleRunRecoveryTask, "cutoffMinutes", 30L);
    }

    @Test
    @DisplayName("Should return 0 when no stale processing runs exist past cutoff")
    void shouldReturnZeroWhenNoStaleRuns() {
        when(settlementRunRepository.findStaleProcessingRuns(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        int recovered = staleRunRecoveryTask.recoverStaleRuns();

        assertThat(recovered).isZero();
        verify(settlementRunRepository, never()).save(any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should recover abandoned PROCESSING runs to FAILED and persist regulatory audit event")
    void shouldRecoverStaleRunsToFailed() {
        Vendor vendor = new Vendor();
        vendor.setId(10L);

        SettlementRun staleRun = SettlementRun.builder()
                .id(101L)
                .vendor(vendor)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .status(SettlementRun.RunStatus.PROCESSING)
                .idempotencyKey("OCT-RUN-PROCESSING-KEY")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        when(settlementRunRepository.findStaleProcessingRuns(any(Instant.class)))
                .thenReturn(List.of(staleRun));

        int recovered = staleRunRecoveryTask.recoverStaleRuns();

        assertThat(recovered).isEqualTo(1);
        assertThat(staleRun.getStatus()).isEqualTo(SettlementRun.RunStatus.FAILED);
        // status and audit verified

        verify(settlementRunRepository).save(staleRun);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());

        AuditEvent persistedAudit = auditCaptor.getValue();
        assertThat(persistedAudit.getAction()).isEqualTo("STALE_RUN_RECOVERED_TO_FAILED");
        assertThat(persistedAudit.getEntityType()).isEqualTo("SettlementRun");
        assertThat(persistedAudit.getEntityId()).isEqualTo(101L);
    }
}

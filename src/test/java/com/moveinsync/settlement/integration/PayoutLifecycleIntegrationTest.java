package com.moveinsync.settlement.integration;

import com.moveinsync.settlement.common.exception.InvalidStateTransitionException;
import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.dto.PayoutFinalizeResponseDto;
import com.moveinsync.settlement.dto.SettlementCalculateRequestDto;
import com.moveinsync.settlement.dto.SettlementRunResponseDto;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.entity.DriverPayslip;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.repository.DriverPayslipRepository;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.service.SettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayoutLifecycleIntegrationTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private DriverPayslipRepository driverPayslipRepository;

    @Autowired
    private PayoutInstructionRepository payoutInstructionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final LocalDate startDate = LocalDate.of(2026, 10, 1);
    private final LocalDate endDate = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("Should execute complete payout state machine: CALCULATED -> UNDER_REVIEW -> APPROVED -> FINALIZED")
    void testCompletePayoutLifecycleFlow() {
        String key = "KEY-LIFECYCLE-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L, startDate, endDate, false
        );

        // 1. Calculate
        SettlementRunResponseDto calculatedRun = settlementService.calculateOrPreviewSettlement(request, key, "OPERATOR");
        assertThat(calculatedRun.getStatus()).isEqualTo(SettlementRun.RunStatus.CALCULATED);

        // 2. Move to Review
        SettlementRunResponseDto reviewedRun = settlementService.moveToReview(calculatedRun.getId(), "FINANCE_ANALYST");
        assertThat(reviewedRun.getStatus()).isEqualTo(SettlementRun.RunStatus.UNDER_REVIEW);

        // 3. Approve
        SettlementRunResponseDto approvedRun = settlementService.approveSettlementRun(reviewedRun.getId(), "FINANCE_DIRECTOR");
        assertThat(approvedRun.getStatus()).isEqualTo(SettlementRun.RunStatus.APPROVED);

        // 4. Finalize
        PayoutFinalizeResponseDto finalizeResponse = settlementService.finalizeSettlementRun(approvedRun.getId(), "CFO");
        assertThat(finalizeResponse.getStatus()).isEqualTo(SettlementRun.RunStatus.FINALIZED);
        assertThat(finalizeResponse.getFinalizedBy()).isEqualTo("CFO");
        assertThat(finalizeResponse.getInstructionsGenerated()).isEqualTo(calculatedRun.getDriverCount());
        assertThat(finalizeResponse.getOutboxEventsQueued()).isGreaterThanOrEqualTo(calculatedRun.getDriverCount() + 1);

        // Verify Payout Instructions persisted
        List<PayoutInstruction> payouts = payoutInstructionRepository.findBySettlementRunId(calculatedRun.getId());
        assertThat(payouts).hasSize(calculatedRun.getDriverCount());
        for (PayoutInstruction payout : payouts) {
            assertThat(payout.getAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(payout.getStatus()).isEqualTo(PayoutInstruction.PayoutStatus.PENDING);
            assertThat(payout.getIdempotencyKey()).startsWith("PAYOUT-RUN-" + calculatedRun.getId());
        }

        // Verify Outbox Events created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);
        assertThat(outboxEvents).anyMatch(e -> "SETTLEMENT_FINALIZED".equals(e.getEventType()) && e.getAggregateId().equals(calculatedRun.getId()));
        assertThat(outboxEvents).anyMatch(e -> "PAYOUT_INITIATED".equals(e.getEventType()));
    }

    @Test
    @DisplayName("Should allow direct approval: CALCULATED -> APPROVED -> FINALIZED")
    void testDirectApprovalFlow() {
        String key = "KEY-DIRECT-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L, startDate, endDate, false
        );

        SettlementRunResponseDto calculatedRun = settlementService.calculateOrPreviewSettlement(request, key, "OPERATOR");
        SettlementRunResponseDto approvedRun = settlementService.approveSettlementRun(calculatedRun.getId(), "SUPERVISOR");
        assertThat(approvedRun.getStatus()).isEqualTo(SettlementRun.RunStatus.APPROVED);

        PayoutFinalizeResponseDto finalizeResponse = settlementService.finalizeSettlementRun(approvedRun.getId(), "DIRECTOR");
        assertThat(finalizeResponse.getStatus()).isEqualTo(SettlementRun.RunStatus.FINALIZED);
    }

    @Test
    @DisplayName("Should reject illegal state transitions with InvalidStateTransitionException")
    void testIllegalStateTransitions() {
        String key = "KEY-ILLEGAL-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L, startDate, endDate, false
        );

        SettlementRunResponseDto calculatedRun = settlementService.calculateOrPreviewSettlement(request, key, "OPERATOR");

        // Attempt to finalize directly from CALCULATED (must fail)
        assertThatThrownBy(() -> settlementService.finalizeSettlementRun(calculatedRun.getId(), "ILLEGAL_CALL"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Must be APPROVED first");

        // Move to review
        settlementService.moveToReview(calculatedRun.getId(), "ANALYST");

        // Attempt to finalize directly from UNDER_REVIEW (must fail)
        assertThatThrownBy(() -> settlementService.finalizeSettlementRun(calculatedRun.getId(), "ILLEGAL_CALL"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Must be APPROVED first");
    }

    @Test
    @DisplayName("Should abort finalization if financial invariant fails (payslip sum != run net)")
    void testFinancialInvariantViolation() {
        String key = "KEY-INVARIANT-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L, startDate, endDate, false
        );

        SettlementRunResponseDto calculatedRun = settlementService.calculateOrPreviewSettlement(request, key, "OPERATOR");
        settlementService.approveSettlementRun(calculatedRun.getId(), "SUPERVISOR");

        // Intentionally tamper with payslip net pay in database to simulate corruption
        DriverPayslip payslip = driverPayslipRepository.findByRunId(calculatedRun.getId()).get(0);
        payslip.setNetPay(payslip.getNetPay().add(new BigDecimal("100.00")));
        driverPayslipRepository.save(payslip);

        // Attempt to finalize must be blocked by invariant check
        assertThatThrownBy(() -> settlementService.finalizeSettlementRun(calculatedRun.getId(), "AUDITOR"))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Financial integrity check failed");
    }
}

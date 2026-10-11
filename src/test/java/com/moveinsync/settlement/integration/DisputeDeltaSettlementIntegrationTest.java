package com.moveinsync.settlement.integration;

import com.moveinsync.settlement.dto.DisputeCreateRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveResponseDto;
import com.moveinsync.settlement.dto.DisputeResponseDto;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.payout.task.OutboxProcessorTask;
import com.moveinsync.settlement.repository.*;
import com.moveinsync.settlement.service.DisputeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DisputeDeltaSettlementIntegrationTest {

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private SettlementAdjustmentRepository settlementAdjustmentRepository;

    @Autowired
    private SettlementLineRepository settlementLineRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private PayoutInstructionRepository payoutInstructionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private OutboxProcessorTask outboxProcessorTask;

    private Trip trip;
    private SettlementLine originalLine;
    private SettlementRun settlementRun;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();
        settlementAdjustmentRepository.deleteAll();
        disputeRepository.deleteAll();

        trip = tripRepository.findById(1L).orElseThrow();
        trip.setStatus(Trip.TripStatus.COMPLETED);
        tripRepository.save(trip);

        settlementRun = settlementRunRepository.findById(1L).orElseGet(() ->
                settlementRunRepository.save(SettlementRun.builder()
                        .vendor(trip.getVendor())
                        .periodStart(LocalDate.of(2026, 10, 1))
                        .periodEnd(LocalDate.of(2026, 10, 31))
                        .idempotencyKey("DISPUTE-RUN-" + UUID.randomUUID())
                        .totalGross(new BigDecimal("70000.00"))
                        .totalNet(new BigDecimal("64775.00"))
                        .tripCount(230)
                        .driverCount(2)
                        .status(SettlementRun.RunStatus.FINALIZED)
                        .build())
        );

        // Ensure trip has an associated settlement line for baseline
        originalLine = settlementLineRepository.findFirstBySourceTypeAndSourceId("TRIP", trip.getId())
                .orElseGet(() -> settlementLineRepository.save(SettlementLine.builder()
                        .run(settlementRun)
                        .driver(trip.getDriver())
                        .lineType(SettlementLine.LineType.BASE_PAY)
                        .sourceType("TRIP")
                        .sourceId(trip.getId())
                        .description("Trip Fare Baseline")
                        .amount(new BigDecimal("250.00"))
                        .build())
                );
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();
        settlementAdjustmentRepository.deleteAll();
        disputeRepository.deleteAll();
        if (trip != null) {
            trip.setStatus(Trip.TripStatus.COMPLETED);
            tripRepository.save(trip);
        }
    }

    @Test
    @DisplayName("Should approve dispute and settle ONLY the delta amount without modifying original historical lines")
    void testApproveDisputeSettlesOnlyDelta() {
        BigDecimal initialLineAmount = originalLine.getAmount();

        // 1. Raise Dispute
        DisputeCreateRequestDto raiseDto = DisputeCreateRequestDto.builder()
                .tripId(trip.getId())
                .reason("Driver was routed through long bypass highway, claiming ₹350 instead of ₹250")
                .evidenceRef("GPS-LOG-TRIP-1-BYPASS")
                .build();

        DisputeResponseDto raised = disputeService.raiseDispute(raiseDto, "DRIVER_RAJ");
        assertThat(raised.getStatus()).isEqualTo(Dispute.DisputeStatus.OPEN);

        Trip disputedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(disputedTrip.getStatus()).isEqualTo(Trip.TripStatus.DISPUTED);

        // 2. Resolve Dispute with Approval (Original: 250.00, Revised: 350.00, Delta: +100.00)
        BigDecimal revisedFare = initialLineAmount.add(new BigDecimal("100.00"));
        DisputeResolveRequestDto resolveDto = DisputeResolveRequestDto.builder()
                .approve(true)
                .revisedAmount(revisedFare)
                .resolutionComments("Route verified by operations supervisor. Approving ₹100 fare delta.")
                .build();

        DisputeResolveResponseDto resolveResult = disputeService.resolveDispute(raised.getId(), resolveDto, "OPS_SUPERVISOR");

        // Verify Dispute state
        assertThat(resolveResult.getDispute().getStatus()).isEqualTo(Dispute.DisputeStatus.APPROVED);
        assertThat(resolveResult.getDispute().getApprovedBy()).isEqualTo("OPS_SUPERVISOR");

        // Verify Trip status restored to COMPLETED
        Trip resolvedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(resolvedTrip.getStatus()).isEqualTo(Trip.TripStatus.COMPLETED);

        // 3. Verify Historical Immutability (Original line item must NEVER change!)
        SettlementLine reloadedOriginalLine = settlementLineRepository.findById(originalLine.getId()).orElseThrow();
        assertThat(reloadedOriginalLine.getAmount()).isEqualByComparingTo(initialLineAmount);

        // 4. Verify SettlementAdjustment (Delta Math)
        assertThat(resolveResult.getAdjustment()).isNotNull();
        assertThat(resolveResult.getAdjustment().getOriginalAmount()).isEqualByComparingTo(initialLineAmount);
        assertThat(resolveResult.getAdjustment().getRevisedAmount()).isEqualByComparingTo(revisedFare);
        assertThat(resolveResult.getAdjustment().getDeltaAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // 5. Verify Closed-Loop Dedicated PayoutInstruction generated for +₹100 delta
        assertThat(resolveResult.getPayoutInstruction()).isNotNull();
        assertThat(resolveResult.getPayoutInstruction().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resolveResult.getPayoutInstruction().getStatus()).isEqualTo(PayoutInstruction.PayoutStatus.PENDING);
        assertThat(resolveResult.getPayoutInstruction().getIdempotencyKey()).isEqualTo("PAYOUT-ADJ-DISPUTE-" + raised.getId());

        // 6. Verify Outbox Worker picks up dispute payout and dispatches to bank
        outboxProcessorTask.processPendingEvents();

        PayoutInstruction reloadedPayout = payoutInstructionRepository.findById(resolveResult.getPayoutInstruction().getId()).orElseThrow();
        assertThat(reloadedPayout.getStatus()).isEqualTo(PayoutInstruction.PayoutStatus.SUCCESS);
        assertThat(reloadedPayout.getProviderReference()).startsWith("UTR-");
    }

    @Test
    @DisplayName("Should reject dispute without creating adjustments or payouts and restore trip status")
    void testRejectDisputeRestoresTripWithoutDelta() {
        DisputeCreateRequestDto raiseDto = DisputeCreateRequestDto.builder()
                .tripId(trip.getId())
                .reason("Driver claims incorrect toll deduction")
                .build();

        DisputeResponseDto raised = disputeService.raiseDispute(raiseDto, "DRIVER_RAJ");

        DisputeResolveRequestDto resolveDto = DisputeResolveRequestDto.builder()
                .approve(false)
                .resolutionComments("Fastag records show no additional toll occurred.")
                .build();

        DisputeResolveResponseDto resolveResult = disputeService.resolveDispute(raised.getId(), resolveDto, "OPS_AUDITOR");

        assertThat(resolveResult.getDispute().getStatus()).isEqualTo(Dispute.DisputeStatus.REJECTED);
        assertThat(resolveResult.getAdjustment()).isNull();
        assertThat(resolveResult.getPayoutInstruction()).isNull();

        Trip restoredTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(restoredTrip.getStatus()).isEqualTo(Trip.TripStatus.COMPLETED);

        // Assert 0 adjustments and 0 payouts created
        assertThat(settlementAdjustmentRepository.count()).isEqualTo(0);
        assertThat(payoutInstructionRepository.count()).isEqualTo(0);
    }
}

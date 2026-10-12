package com.moveinsync.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.common.exception.ResourceNotFoundException;
import com.moveinsync.settlement.calculation.DisputeDeltaCalculator;
import com.moveinsync.settlement.common.metrics.SettlementMetrics;
import com.moveinsync.settlement.dto.DisputeCreateRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveResponseDto;
import com.moveinsync.settlement.dto.DisputeResponseDto;
import com.moveinsync.settlement.dto.PayoutInstructionResponseDto;
import com.moveinsync.settlement.dto.SettlementAdjustmentResponseDto;
import com.moveinsync.settlement.entity.Dispute;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.entity.SettlementAdjustment;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.repository.DisputeRepository;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import com.moveinsync.settlement.repository.SettlementAdjustmentRepository;
import com.moveinsync.settlement.repository.SettlementLineRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing trip billing disputes and delta adjustments.
 * Keeps historical settlement records immutable and creates adjustments
 * for the difference amount when disputes are resolved.
 *
 * @author Deepanshu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final SettlementAdjustmentRepository settlementAdjustmentRepository;
    private final SettlementLineRepository settlementLineRepository;
    private final SettlementMetrics settlementMetrics;
    private final SettlementRunRepository settlementRunRepository;
    private final TripRepository tripRepository;
    private final PayoutInstructionRepository payoutInstructionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DisputeDeltaCalculator disputeDeltaCalculator;
    private final ObjectMapper objectMapper;

    @Transactional
    @Audited(entityType = "DISPUTE", action = "RAISE")
    @ExecutionTime
    @SuppressWarnings("null")
    public DisputeResponseDto raiseDispute(DisputeCreateRequestDto dto, String username) {
        Trip trip = tripRepository.findById(dto.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + dto.getTripId()));

        SettlementRun run = null;
        if (dto.getSettlementRunId() != null) {
            run = settlementRunRepository.findById(dto.getSettlementRunId())
                    .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found with id: " + dto.getSettlementRunId()));
        } else {
            run = settlementLineRepository.findFirstBySourceTypeAndSourceId("TRIP", trip.getId())
                    .map(line -> line.getRun())
                    .orElse(null);
        }

        Dispute dispute = Dispute.builder()
                .trip(trip)
                .settlementRun(run)
                .raisedBy(username != null ? username : "DRIVER")
                .reason(dto.getReason())
                .evidenceRef(dto.getEvidenceRef())
                .status(Dispute.DisputeStatus.OPEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Dispute savedDispute = disputeRepository.save(dispute);
        settlementMetrics.incrementDisputesRaised();

        // Encapsulated state transition: mark trip as disputed
        trip.markDisputed();
        tripRepository.save(trip);

        log.info("Raised dispute {} on trip {} by {}", savedDispute.getId(), trip.getId(), username);
        return DisputeResponseDto.fromEntity(savedDispute);
    }

    @Transactional
    @Audited(entityType = "DISPUTE", action = "RESOLVE")
    @ExecutionTime
    @SuppressWarnings("null")
    public DisputeResolveResponseDto resolveDispute(Long disputeId, DisputeResolveRequestDto dto, String username) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeId));

        Trip trip = dispute.getTrip();

        // Dispute Rejected Flow
        if (Boolean.FALSE.equals(dto.getApprove())) {
            dispute.reject(username, dto.getResolutionComments());
            settlementMetrics.incrementDisputesRejected();
            disputeRepository.save(dispute);

            trip.restoreCompleted();
            tripRepository.save(trip);

            log.info("Rejected dispute {} on trip {}", disputeId, trip.getId());
            return DisputeResolveResponseDto.builder()
                    .dispute(DisputeResponseDto.fromEntity(dispute))
                    .adjustment(null)
                    .payoutInstruction(null)
                    .build();
        }

        // Dispute Approved Flow - Pure SRP Delta Calculation
        BigDecimal originalAmount = settlementLineRepository.findFirstBySourceTypeAndSourceId("TRIP", trip.getId())
                .map(line -> line.getAmount())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        DisputeDeltaCalculator.DisputeDeltaResult deltaResult = disputeDeltaCalculator.calculateDelta(
                originalAmount, dto.getRevisedAmount()
        );

        log.info("Dispute {} approved: originalAmount=₹{}, revisedAmount=₹{}, delta=₹{}",
                disputeId, deltaResult.getOriginalAmount(), deltaResult.getRevisedAmount(), deltaResult.getDeltaAmount());

        // 1. Create Append-Only SettlementAdjustment (Historical lines remain 100% IMMUTABLE)
        settlementMetrics.incrementDisputesResolved();
        SettlementAdjustment adjustment = SettlementAdjustment.builder()
                .dispute(dispute)
                .originalRun(dispute.getSettlementRun())
                .driver(trip.getDriver())
                .lineType("BASE_FARE_ADJUSTMENT")
                .originalAmount(deltaResult.getOriginalAmount())
                .revisedAmount(deltaResult.getRevisedAmount())
                .deltaAmount(deltaResult.getDeltaAmount())
                .reason(dto.getResolutionComments() != null ? dto.getResolutionComments() : "Dispute approved rate adjustment")
                .version(1)
                .createdAt(Instant.now())
                .build();
        SettlementAdjustment savedAdjustment = settlementAdjustmentRepository.save(adjustment);

        // 2. Closed-Loop Payout Generation for Delta Amount
        PayoutInstruction savedPayout = null;
        if (deltaResult.isPayable()) {
            String payoutKey = "PAYOUT-ADJ-DISPUTE-" + dispute.getId();
            PayoutInstruction payout = PayoutInstruction.builder()
                    .settlementRun(dispute.getSettlementRun())
                    .vendor(trip.getVendor())
                    .driver(trip.getDriver())
                    .amount(deltaResult.getDeltaAmount())
                    .currency("INR")
                    .idempotencyKey(payoutKey)
                    .status(PayoutInstruction.PayoutStatus.PENDING)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            savedPayout = payoutInstructionRepository.save(payout);

            // Queue Transactional Outbox Event for immediate delta disbursement
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("payoutId", savedPayout.getId());
            payloadMap.put("driverId", trip.getDriver().getId());
            payloadMap.put("beneficiaryRef", trip.getDriver().getExternalRef());
            payloadMap.put("amount", savedPayout.getAmount());
            payloadMap.put("currency", savedPayout.getCurrency());
            payloadMap.put("idempotencyKey", savedPayout.getIdempotencyKey());
            payloadMap.put("disputeId", dispute.getId());

            try {
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .aggregateType("PAYOUT_INSTRUCTION")
                        .aggregateId(savedPayout.getId())
                        .eventType("PAYOUT_INITIATED")
                        .payload(objectMapper.writeValueAsString(payloadMap))
                        .status(OutboxEvent.OutboxStatus.PENDING)
                        .retryCount(0)
                        .createdAt(Instant.now())
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (Exception e) {
                log.error("Failed to serialize outbox event for dispute payout {}", savedPayout.getId(), e);
            }
        }

        // 3. Encapsulated State Transitions on Dispute & Trip
        dispute.approve(username, dto.getResolutionComments());
        disputeRepository.save(dispute);

        trip.restoreCompleted();
        tripRepository.save(trip);

        return DisputeResolveResponseDto.builder()
                .dispute(DisputeResponseDto.fromEntity(dispute))
                .adjustment(SettlementAdjustmentResponseDto.fromEntity(savedAdjustment))
                .payoutInstruction(savedPayout != null ? PayoutInstructionResponseDto.fromEntity(savedPayout) : null)
                .build();
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public DisputeResponseDto getDispute(Long id) {
        return disputeRepository.findById(id)
                .map(DisputeResponseDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + id));
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<DisputeResponseDto> listDisputesByTrip(Long tripId) {
        return disputeRepository.findByTripId(tripId).stream()
                .map(DisputeResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<DisputeResponseDto> listOpenDisputes() {
        return disputeRepository.findByStatus(Dispute.DisputeStatus.OPEN).stream()
                .map(DisputeResponseDto::fromEntity)
                .toList();
    }
}

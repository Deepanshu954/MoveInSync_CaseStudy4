package com.moveinsync.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.moveinsync.settlement.calculation.BasePayCalculator;
import com.moveinsync.settlement.calculation.CalculatedLineItem;
import com.moveinsync.settlement.calculation.IncentiveCalculator;
import com.moveinsync.settlement.calculation.PenaltyCalculator;
import com.moveinsync.settlement.calculation.TaxCalculator;
import com.moveinsync.settlement.payout.validator.FinancialInvariantValidator;
import com.moveinsync.settlement.common.exception.InvalidStateTransitionException;
import com.moveinsync.settlement.common.exception.ResourceNotFoundException;
import com.moveinsync.settlement.dto.DriverPayslipResponseDto;
import com.moveinsync.settlement.dto.SettlementCalculateRequestDto;
import com.moveinsync.settlement.dto.SettlementLineResponseDto;
import com.moveinsync.settlement.dto.SettlementRunResponseDto;
import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.dto.PayoutFinalizeResponseDto;
import com.moveinsync.settlement.dto.PayoutInstructionResponseDto;
import com.moveinsync.settlement.service.PayoutService;

import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core orchestration engine for MoveInSync vendor and driver settlements.
 *
 * <p>Key architectural guarantees:
 * <ul>
 *   <li><b>Tier-2 Domain Scope Protection:</b> Re-calculating an already-settled billing period
 *       safely returns the existing run without duplicate ledger records.</li>
 *   <li><b>Preview Mode:</b> Evaluates complete ledger calculations in-memory without database writes.</li>
 *   <li><b>Batch Persistence & N+1 Elimination:</b> Trips are bulk fetched with JOIN FETCH,
 *       and generated payslips and lines are saved using {@code saveAll()} batch operations.</li>
 *   <li><b>Zero Mathematical Drift:</b> Net pay, payslips, and run totals reconcile with 0.00 paise drift.</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SettlementService {

        private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final SettlementRunRepository settlementRunRepository;
    private final DriverPayslipRepository driverPayslipRepository;
    private final SettlementLineRepository settlementLineRepository;
    private final TripRepository tripRepository;
    private final TripViolationRepository tripViolationRepository;
    private final VendorRepository vendorRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final ContractRepository contractRepository;
    private final PolicyCacheService policyCacheService;

    private final BasePayCalculator basePayCalculator;
    private final IncentiveCalculator incentiveCalculator;
    private final PenaltyCalculator penaltyCalculator;
    private final TaxCalculator taxCalculator;
    private final ObjectMapper objectMapper;
    private final PayoutInstructionRepository payoutInstructionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PayoutService payoutService;
    private final FinancialInvariantValidator financialInvariantValidator;



    /**
     * Executes or previews a settlement calculation for a vendor and billing window.
     */
    @Transactional
    @ExecutionTime("SettlementBatchCalculation")
    @Audited(entityType = "SETTLEMENT_RUN", action = "CALCULATE")
    public SettlementRunResponseDto calculateOrPreviewSettlement(SettlementCalculateRequestDto request,
                                                                 String idempotencyKey,
                                                                 String initiatedBy) {
        log.info("Starting settlement calculation: vendorId={}, buId={}, period={}-{}, previewOnly={}",
                request.getVendorId(), request.getBusinessUnitId(),
                request.getPeriodStart(), request.getPeriodEnd(), request.isPreviewOnly());

        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + request.getVendorId()));

        BusinessUnit businessUnit = null;
        if (request.getBusinessUnitId() != null) {
            businessUnit = businessUnitRepository.findById(request.getBusinessUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("BusinessUnit not found with id: " + request.getBusinessUnitId()));
        }

        // TIER-2 DOMAIN SCOPE PROTECTION: Check for existing original run
        if (!request.isPreviewOnly()) {
            Optional<SettlementRun> existingRunOpt = settlementRunRepository.findExistingOriginalRun(
                    request.getVendorId(),
                    request.getBusinessUnitId(),
                    request.getPeriodStart(),
                    request.getPeriodEnd()
            );

            if (existingRunOpt.isPresent()) {
                SettlementRun existing = existingRunOpt.get();
                log.info("Found existing SettlementRun {} for scope. Returning existing run (Tier-2 Idempotency).", existing.getId());
                return getSettlementRun(existing.getId());
            }
        }

        // Temporal boundary conversion (IST)
        Instant periodStartInstant = request.getPeriodStart().atStartOfDay(IST_ZONE).toInstant();
        Instant periodEndInstant = request.getPeriodEnd().plusDays(1).atStartOfDay(IST_ZONE).toInstant();

        // 1. Fetch eligible trips with JOIN FETCH to prevent N+1 queries
        List<Trip> trips = tripRepository.findEligibleTrips(
                request.getVendorId(),
                request.getBusinessUnitId(),
                periodStartInstant,
                periodEndInstant
        );
        log.info("Found {} eligible trips for settlement scope", trips.size());

        // 2. Fetch cached policies and contract rates
        List<Contract> contracts = contractRepository.findByVendorId(request.getVendorId());
        List<Long> contractIds = contracts.stream().map(Contract::getId).toList();
        List<ContractRate> contractRates = policyCacheService.getContractRates(contractIds);

        List<IncentivePolicy> incentivePolicies = policyCacheService.getIncentivePolicies(
                request.getVendorId(), request.getBusinessUnitId(), periodEndInstant
        );

        List<PenaltyPolicy> penaltyPolicies = policyCacheService.getPenaltyPolicies(
                request.getVendorId(), request.getBusinessUnitId(), periodEndInstant
        );

        Optional<TaxConfig> taxConfig = policyCacheService.getTaxConfig(periodEndInstant);

        // 3. Group trips by Driver [O(T)]
        Map<Driver, List<Trip>> tripsByDriver = trips.stream()
                .collect(Collectors.groupingBy(Trip::getDriver, LinkedHashMap::new, Collectors.toList()));

        // Run totals accumulator
        BigDecimal runTotalGross = BigDecimal.ZERO;
        BigDecimal runTotalIncentives = BigDecimal.ZERO;
        BigDecimal runTotalPenalties = BigDecimal.ZERO;
        BigDecimal runTotalTds = BigDecimal.ZERO;
        BigDecimal runTotalNet = BigDecimal.ZERO;
        int runTotalTrips = trips.size();
        int runTotalDrivers = tripsByDriver.size();

        List<DriverPayslipResponseDto> ephemeralPayslips = new ArrayList<>();
        List<DriverCalculationHolder> calculationHolders = new ArrayList<>();

        // 4. Driver Calculation Loop
        for (Map.Entry<Driver, List<Trip>> entry : tripsByDriver.entrySet()) {
            Driver driver = entry.getKey();
            List<Trip> driverTrips = entry.getValue();

            List<CalculatedLineItem> driverLines = new ArrayList<>();

            // A. Base Trip Fares
            List<CalculatedLineItem> basePayLines = basePayCalculator.calculate(driverTrips, contractRates);
            driverLines.addAll(basePayLines);
            BigDecimal driverGross = basePayLines.stream()
                    .map(CalculatedLineItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // B. Incentives
            List<CalculatedLineItem> incentiveLines = incentiveCalculator.calculate(
                    driverTrips, incentivePolicies, request.getPeriodStart(), request.getPeriodEnd()
            );
            driverLines.addAll(incentiveLines);
            BigDecimal driverIncentives = incentiveLines.stream()
                    .map(CalculatedLineItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // C. Penalties
            List<TripViolation> driverViolations = tripViolationRepository.findDriverViolationsInPeriod(
                    driver.getId(), periodStartInstant, periodEndInstant
            );
            List<CalculatedLineItem> penaltyLines = penaltyCalculator.calculate(driverViolations, penaltyPolicies);
            driverLines.addAll(penaltyLines);
            // Penalties are signed negative, convert to absolute for payslip debit summary
            BigDecimal driverPenaltiesAbs = penaltyLines.stream()
                    .map(CalculatedLineItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .abs();

            // D. Statutory TDS (Section 194C)
            BigDecimal taxableEarnings = driverGross.add(driverIncentives);
            BigDecimal driverTdsAbs = BigDecimal.ZERO;
            if (taxConfig.isPresent()) {
                Optional<CalculatedLineItem> tdsLineOpt = taxCalculator.calculate(driver, taxableEarnings, taxConfig.get());
                if (tdsLineOpt.isPresent()) {
                    CalculatedLineItem tdsLine = tdsLineOpt.get();
                    driverLines.add(tdsLine);
                    driverTdsAbs = tdsLine.getAmount().abs();
                }
            }

            // Net Pay = Gross + Incentives - Penalties - TDS
            BigDecimal driverNetPay = driverGross
                    .add(driverIncentives)
                    .subtract(driverPenaltiesAbs)
                    .subtract(driverTdsAbs)
                    .setScale(2, RoundingMode.HALF_UP);

            // Accumulate into Run Totals
            runTotalGross = runTotalGross.add(driverGross);
            runTotalIncentives = runTotalIncentives.add(driverIncentives);
            runTotalPenalties = runTotalPenalties.add(driverPenaltiesAbs);
            runTotalTds = runTotalTds.add(driverTdsAbs);
            runTotalNet = runTotalNet.add(driverNetPay);

            DriverCalculationHolder holder = new DriverCalculationHolder(
                    driver, driverGross, driverIncentives, driverPenaltiesAbs, driverTdsAbs, driverNetPay, driverTrips.size(), driverLines
            );
            calculationHolders.add(holder);

            // Prepare Ephemeral DTO representation
            List<SettlementLineResponseDto> lineDtos = driverLines.stream()
                    .map(l -> new SettlementLineResponseDto(
                            null, l.getLineType(), l.getSourceType(), l.getSourceId(),
                            l.getDescription(), l.getAmount(), l.getRateVersion(),
                            l.getPolicyVersion(), l.getCalculationDetails()
                    ))
                    .toList();

            DriverPayslipResponseDto payslipDto = new DriverPayslipResponseDto(
                    null, driver.getId(), driver.getName(), driver.getExternalRef(),
                    driverGross, driverIncentives, driverPenaltiesAbs, driverTdsAbs, driverNetPay,
                    driverTrips.size(), lineDtos
            );
            ephemeralPayslips.add(payslipDto);
        }

        // 5. If Preview Mode, return directly without database persistence
        if (request.isPreviewOnly()) {
            log.info("Returning preview settlement calculation for vendor {}: netAmount={}",
                    vendor.getId(), runTotalNet);
            return new SettlementRunResponseDto(
                    null, vendor.getId(), vendor.getCode(), vendor.getName(),
                    businessUnit != null ? businessUnit.getId() : null,
                    businessUnit != null ? businessUnit.getCode() : null,
                    request.getPeriodStart(), request.getPeriodEnd(),
                    SettlementRun.RunType.ORIGINAL,
                    SettlementRun.RunStatus.PENDING,
                    idempotencyKey,
                    runTotalGross, runTotalIncentives, runTotalPenalties, runTotalTds, runTotalNet,
                    runTotalTrips, runTotalDrivers, Instant.now(), ephemeralPayslips
            );
        }

        // 6. Persistent Settlement Run Save
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        SettlementRun run = new SettlementRun(
                null,
                vendor,
                businessUnit,
                request.getPeriodStart(),
                request.getPeriodEnd(),
                SettlementRun.RunType.ORIGINAL,
                SettlementRun.RunStatus.CALCULATED,
                effectiveKey,
                runTotalGross,
                runTotalIncentives,
                runTotalPenalties,
                runTotalTds,
                runTotalNet,
                runTotalTrips,
                runTotalDrivers,
                "v1.0.0",
                null,
                initiatedBy != null ? initiatedBy : "SYSTEM",
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        SettlementRun savedRun = settlementRunRepository.save(run);

        // 7. Batch Save Payslips & Settlement Lines
        List<DriverPayslip> payslipsToSave = new ArrayList<>();
        List<SettlementLine> linesToSave = new ArrayList<>();

        for (DriverCalculationHolder h : calculationHolders) {
            DriverPayslip payslip = new DriverPayslip(
                    null,
                    savedRun,
                    h.driver,
                    h.grossPay,
                    h.incentives,
                    h.penalties,
                    h.tds,
                    h.netPay,
                    h.tripCount,
                    Instant.now()
            );
            payslipsToSave.add(payslip);

            for (CalculatedLineItem l : h.lines) {
                SettlementLine line = new SettlementLine(
                        null,
                        savedRun,
                        h.driver,
                        l.getLineType(),
                        l.getSourceType(),
                        l.getSourceId(),
                        l.getDescription(),
                        l.getAmount(),
                        l.getRateVersion(),
                        l.getPolicyVersion(),
                        l.getCalculationDetails(),
                        Instant.now()
                );
                linesToSave.add(line);
            }
        }

        driverPayslipRepository.saveAll(payslipsToSave);
        settlementLineRepository.saveAll(linesToSave);

        log.info("Persisted SettlementRun {}: {} payslips, {} lines, netAmount={}",
                savedRun.getId(), payslipsToSave.size(), linesToSave.size(), savedRun.getTotalNet());

        return getSettlementRun(savedRun.getId());
    }

    /**
     * Eagerly loads a complete settlement run with all payslips and line items.
     * Uses a single query for lines with memory grouping to eliminate Hibernate cascades.
     */
    @Transactional(readOnly = true)
    public SettlementRunResponseDto getSettlementRun(Long runId) {
        SettlementRun run = settlementRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found with id: " + runId));

        List<DriverPayslip> payslips = driverPayslipRepository.findByRunId(runId);
        List<SettlementLine> allLines = settlementLineRepository.findByRunIdOrderByDriverIdAsc(runId);

        // Group lines by driver in memory [O(L)]
        Map<Long, List<SettlementLine>> linesByDriver = allLines.stream()
                .collect(Collectors.groupingBy(l -> l.getDriver().getId()));

        List<DriverPayslipResponseDto> payslipDtos = payslips.stream()
                .map(p -> {
                    List<SettlementLine> driverLines = linesByDriver.getOrDefault(p.getDriver().getId(), Collections.emptyList());
                    List<SettlementLineResponseDto> lineDtos = driverLines.stream()
                            .map(l -> new SettlementLineResponseDto(
                                    l.getId(), l.getLineType(), l.getSourceType(), l.getSourceId(),
                                    l.getDescription(), l.getAmount(), l.getRateVersion(),
                                    l.getPolicyVersion(), l.getCalculationDetails()
                            ))
                            .toList();

                    return new DriverPayslipResponseDto(
                            p.getId(), p.getDriver().getId(), p.getDriver().getName(),
                            p.getDriver().getExternalRef(), p.getGrossPay(), p.getTotalIncentives(),
                            p.getTotalPenalties(), p.getTotalTds(), p.getNetPay(),
                            p.getTripCount(), lineDtos
                    );
                })
                .toList();

        return new SettlementRunResponseDto(
                run.getId(),
                run.getVendor().getId(),
                run.getVendor().getCode(),
                run.getVendor().getName(),
                run.getBusinessUnit() != null ? run.getBusinessUnit().getId() : null,
                run.getBusinessUnit() != null ? run.getBusinessUnit().getCode() : null,
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getRunType(),
                run.getStatus(),
                run.getIdempotencyKey(),
                run.getTotalGross(),
                run.getTotalIncentives(),
                run.getTotalPenalties(),
                run.getTotalTds(),
                run.getTotalNet(),
                run.getTripCount(),
                run.getDriverCount(),
                run.getCreatedAt(),
                payslipDtos
        );
    }

    /**
     * Approves a CALCULATED or UNDER_REVIEW settlement run with pessimistic locking.
     */
    @Transactional
    @Audited(entityType = "SETTLEMENT_RUN", action = "APPROVE")
    public SettlementRunResponseDto approveSettlementRun(Long runId, String approvedBy) {
        SettlementRun run = settlementRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found with id: " + runId));

        run.approve(approvedBy);
        settlementRunRepository.save(run);

        log.info("Approved SettlementRun {} by {}", runId, approvedBy);
        return getSettlementRun(runId);
    }

    
    /**
     * Transitions a CALCULATED settlement run to UNDER_REVIEW with pessimistic locking.
     */
    @Transactional
    @Audited(entityType = "SETTLEMENT_RUN", action = "MOVE_TO_REVIEW")
    @ExecutionTime
    public SettlementRunResponseDto moveToReview(Long runId, String reviewedBy) {
        SettlementRun run = settlementRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found with id: " + runId));

        run.moveToReview(reviewedBy);
        settlementRunRepository.save(run);

        log.info("SettlementRun {} transitioned to UNDER_REVIEW by {}", runId, reviewedBy);
        return getSettlementRun(runId);
    }

    /**
     * Finalizes an APPROVED settlement run with pessimistic locking, strict invariant verification,
     * generation of banking payout instructions, and queuing of Transactional Outbox events.
     */
    @Transactional
    @Audited(entityType = "SETTLEMENT_RUN", action = "FINALIZE")
    @ExecutionTime
    public PayoutFinalizeResponseDto finalizeSettlementRun(Long runId, String finalizedBy) {
        SettlementRun run = settlementRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found with id: " + runId));

        if (run.getStatus() != SettlementRun.RunStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "Cannot finalize SettlementRun in status: " + run.getStatus() + ". Must be APPROVED first."
            );
        }

        // 1. Strict Financial Invariant Verification via SRP Component
        List<DriverPayslip> payslips = driverPayslipRepository.findByRunId(runId);
        financialInvariantValidator.validateZeroDrift(run, payslips);

        // 2. Transition Run State via Domain Encapsulation
        run.finalizeRun(finalizedBy);
        SettlementRun savedRun = settlementRunRepository.save(run);

        // 3. Generate Payout Instructions for eligible drivers (Net Pay > 0)
        List<PayoutInstruction> payoutInstructions = new ArrayList<>();
        for (DriverPayslip payslip : payslips) {
            if (payslip.getNetPay().compareTo(BigDecimal.ZERO) > 0) {
                String payoutKey = String.format("PAYOUT-RUN-%d-DRIVER-%d", runId, payslip.getDriver().getId());
                PayoutInstruction instruction = PayoutInstruction.builder()
                        .settlementRun(savedRun)
                        .vendor(savedRun.getVendor())
                        .driver(payslip.getDriver())
                        .amount(payslip.getNetPay())
                        .currency("INR")
                        .idempotencyKey(payoutKey)
                        .status(PayoutInstruction.PayoutStatus.PENDING)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                payoutInstructions.add(instruction);
            }
        }
        List<PayoutInstruction> savedPayouts = payoutInstructionRepository.saveAll(payoutInstructions);

        // 4. Generate Outbox Events (Atomic with Finalization)
        List<OutboxEvent> outboxEvents = new ArrayList<>();

        // Event A: SETTLEMENT_FINALIZED aggregate event
        Map<String, Object> runEventPayload = new HashMap<>();
        runEventPayload.put("runId", savedRun.getId());
        runEventPayload.put("vendorId", savedRun.getVendor().getId());
        runEventPayload.put("totalNet", savedRun.getTotalNet());
        runEventPayload.put("driverCount", savedRun.getDriverCount());
        runEventPayload.put("finalizedBy", savedRun.getFinalizedBy());
        runEventPayload.put("finalizedAt", Instant.now().toString());

        try {
            outboxEvents.add(OutboxEvent.builder()
                    .aggregateType("SETTLEMENT_RUN")
                    .aggregateId(savedRun.getId())
                    .eventType("SETTLEMENT_FINALIZED")
                    .payload(objectMapper.writeValueAsString(runEventPayload))
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to serialize outbox event payload for run {}", runId, e);
        }

        // Event B: PAYOUT_INITIATED for each payout instruction
        for (PayoutInstruction p : savedPayouts) {
            Map<String, Object> payoutEventPayload = new HashMap<>();
            payoutEventPayload.put("payoutId", p.getId());
            payoutEventPayload.put("runId", savedRun.getId());
            payoutEventPayload.put("driverId", p.getDriver() != null ? p.getDriver().getId() : null);
            payoutEventPayload.put("beneficiaryRef", p.getDriver() != null ? p.getDriver().getExternalRef() : null);
            payoutEventPayload.put("amount", p.getAmount());
            payoutEventPayload.put("currency", p.getCurrency());
            payoutEventPayload.put("idempotencyKey", p.getIdempotencyKey());

            try {
                outboxEvents.add(OutboxEvent.builder()
                        .aggregateType("PAYOUT_INSTRUCTION")
                        .aggregateId(p.getId())
                        .eventType("PAYOUT_INITIATED")
                        .payload(objectMapper.writeValueAsString(payoutEventPayload))
                        .status(OutboxEvent.OutboxStatus.PENDING)
                        .retryCount(0)
                        .createdAt(Instant.now())
                        .build());
            } catch (Exception e) {
                log.error("Failed to serialize outbox payload for payout {}", p.getId(), e);
            }
        }

        outboxEventRepository.saveAll(outboxEvents);

        log.info("Finalized SettlementRun {}: generated {} payout instructions and {} outbox events",
                runId, savedPayouts.size(), outboxEvents.size());

        List<PayoutInstructionResponseDto> instructionDtos = savedPayouts.stream()
                .map(PayoutInstructionResponseDto::fromEntity)
                .toList();

        return PayoutFinalizeResponseDto.builder()
                .settlementRunId(savedRun.getId())
                .status(savedRun.getStatus())
                .totalDisbursed(savedRun.getTotalNet())
                .instructionsGenerated(savedPayouts.size())
                .outboxEventsQueued(outboxEvents.size())
                .finalizedBy(savedRun.getFinalizedBy())
                .finalizedAt(savedRun.getUpdatedAt())
                .instructions(instructionDtos)
                .build();
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<PayoutInstructionResponseDto> getRunPayouts(Long runId) {
        return payoutService.getPayoutsByRunId(runId);
    }

    private static class DriverCalculationHolder {
        final Driver driver;
        final BigDecimal grossPay;
        final BigDecimal incentives;
        final BigDecimal penalties;
        final BigDecimal tds;
        final BigDecimal netPay;
        final int tripCount;
        final List<CalculatedLineItem> lines;

        DriverCalculationHolder(Driver driver, BigDecimal grossPay, BigDecimal incentives,
                                BigDecimal penalties, BigDecimal tds, BigDecimal netPay,
                                int tripCount, List<CalculatedLineItem> lines) {
            this.driver = driver;
            this.grossPay = grossPay;
            this.incentives = incentives;
            this.penalties = penalties;
            this.tds = tds;
            this.netPay = netPay;
            this.tripCount = tripCount;
            this.lines = lines;
        }
    }
}

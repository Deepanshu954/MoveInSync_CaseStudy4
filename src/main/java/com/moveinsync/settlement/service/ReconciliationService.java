package com.moveinsync.settlement.service;

import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.common.exception.DuplicateResourceException;
import com.moveinsync.settlement.common.exception.ResourceNotFoundException;
import com.moveinsync.settlement.dto.ReconcileRequestDto;
import com.moveinsync.settlement.dto.ReconciliationResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceSubmitRequestDto;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.calculation.ReconciliationCalculator;
import com.moveinsync.settlement.common.metrics.SettlementMetrics;
import com.moveinsync.settlement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Enterprise service managing vendor invoice submissions and three-way
 * reconciliation matching.
 *
 * <p>
 * Validates month-end vendor invoice claims against engine-calculated
 * disbursement ledgers.
 * If variance falls within tolerance, the invoice automatically transitions to
 * {@code MATCHED}.
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {

        private final VendorInvoiceRepository vendorInvoiceRepository;
        private final ReconciliationRecordRepository reconciliationRecordRepository;
        private final SettlementRunRepository settlementRunRepository;
        private final VendorRepository vendorRepository;
        private final BusinessUnitRepository businessUnitRepository;
        private final SettlementMetrics settlementMetrics;
        private final ReconciliationCalculator reconciliationCalculator;

        @Transactional
        @Audited(entityType = "VENDOR_INVOICE", action = "SUBMIT")
        @ExecutionTime
        public VendorInvoiceResponseDto submitInvoice(VendorInvoiceSubmitRequestDto dto) {
                vendorInvoiceRepository.findByVendorIdAndInvoiceRef(dto.getVendorId(), dto.getInvoiceRef())
                                .ifPresent(existing -> {
                                        throw new DuplicateResourceException("Invoice with ref '" + dto.getInvoiceRef()
                                                        + "' already exists for vendor " + dto.getVendorId());
                                });

                Vendor vendor = vendorRepository.findById(dto.getVendorId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Vendor not found with id: " + dto.getVendorId()));

                BusinessUnit bu = null;
                if (dto.getBusinessUnitId() != null) {
                        bu = businessUnitRepository.findById(dto.getBusinessUnitId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "BusinessUnit not found with id: " + dto.getBusinessUnitId()));
                } else {
                        List<BusinessUnit> bus = businessUnitRepository.findByVendorId(dto.getVendorId());
                        bu = !bus.isEmpty() ? bus.get(0) : businessUnitRepository.findAll().stream().findFirst().orElse(null);
                }

                VendorInvoice invoice = VendorInvoice.builder()
                                .vendor(vendor)
                                .businessUnit(bu)
                                .invoiceRef(dto.getInvoiceRef())
                                .periodStart(dto.getPeriodStart())
                                .periodEnd(dto.getPeriodEnd())
                                .amount(dto.getAmount().setScale(2, RoundingMode.HALF_UP))
                                .currency(dto.getCurrency() != null ? dto.getCurrency() : "INR")
                                .status(VendorInvoice.InvoiceStatus.PENDING)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();

                VendorInvoice saved = vendorInvoiceRepository.save(invoice);
                log.info("Submitted vendor invoice {} for vendor {}: amount={}", saved.getId(), vendor.getId(),
                                saved.getAmount());
                return VendorInvoiceResponseDto.fromEntity(saved);
        }

        @Transactional
        @Audited(entityType = "RECONCILIATION", action = "RECONCILE")
        @ExecutionTime
        public ReconciliationResponseDto reconcileInvoice(ReconcileRequestDto dto, String username) {
                VendorInvoice invoice = vendorInvoiceRepository.findById(dto.getInvoiceId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "VendorInvoice not found with id: " + dto.getInvoiceId()));

                SettlementRun run = settlementRunRepository.findById(dto.getSettlementRunId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "SettlementRun not found with id: " + dto.getSettlementRunId()));

                BigDecimal calculatedAmount = "GROSS".equalsIgnoreCase(dto.getAccountingBasis())
                                ? run.getTotalGross()
                                : run.getTotalNet();

                // Delegate variance and tolerance evaluation to pure SRP calculator
                ReconciliationCalculator.ReconciliationResult result = reconciliationCalculator.calculate(
                                calculatedAmount,
                                invoice.getAmount(),
                                dto.getTolerance(),
                                dto.getTolerancePercentage());

                // Update entity state via domain encapsulation
                if (result.isMatched()) {
                        invoice.markMatched();
                        settlementMetrics.incrementReconciliationsMatched();
                } else {
                        invoice.markDiscrepancy();
                        settlementMetrics.incrementReconciliationsDiscrepancy();
                }
                vendorInvoiceRepository.save(invoice);

                ReconciliationRecord record = ReconciliationRecord.builder()
                                .invoice(invoice)
                                .settlementRun(run)
                                .calculatedAmount(result.getCalculatedAmount())
                                .invoiceAmount(result.getInvoiceAmount())
                                .difference(result.getDifference())
                                .tolerance(result.getEffectiveTolerance())
                                .status(result.getStatus())
                                .accountingBasis(dto.getAccountingBasis() != null ? dto.getAccountingBasis()
                                                : "NET_DRIVER_PAYOUT")
                                .notes(result.getNotes())
                                .resolvedBy(username != null ? username : "FINANCE_APPROVER")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();

                ReconciliationRecord savedRecord = reconciliationRecordRepository.save(record);
                log.info("Completed reconciliation for invoice {}: status={}, difference={}",
                                invoice.getId(), result.getStatus(), result.getDifference());

                return ReconciliationResponseDto.fromEntity(savedRecord);
        }

        @Transactional(readOnly = true)
        @ExecutionTime
        public VendorInvoiceResponseDto getInvoice(Long id) {
                return vendorInvoiceRepository.findById(id)
                                .map(VendorInvoiceResponseDto::fromEntity)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "VendorInvoice not found with id: " + id));
        }

        @Transactional(readOnly = true)
        @ExecutionTime
        public List<VendorInvoiceResponseDto> listInvoicesByVendor(Long vendorId) {
                return vendorInvoiceRepository.findByVendorId(vendorId).stream()
                                .map(VendorInvoiceResponseDto::fromEntity)
                                .toList();
        }

        @Transactional(readOnly = true)
        @ExecutionTime
        public ReconciliationResponseDto getReconciliationRecord(Long id) {
                return reconciliationRecordRepository.findById(id)
                                .map(ReconciliationResponseDto::fromEntity)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "ReconciliationRecord not found with id: " + id));
        }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<VendorInvoiceResponseDto> listAllInvoices() {
        return vendorInvoiceRepository.findAll().stream()
                .map(VendorInvoiceResponseDto::fromEntity)
                .toList();
    }
}
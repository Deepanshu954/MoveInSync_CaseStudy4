package com.moveinsync.settlement.integration;

import com.moveinsync.settlement.dto.ReconcileRequestDto;
import com.moveinsync.settlement.dto.ReconciliationResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceSubmitRequestDto;
import com.moveinsync.settlement.entity.ReconciliationRecord;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.entity.VendorInvoice;
import com.moveinsync.settlement.repository.ReconciliationRecordRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorInvoiceRepository;
import com.moveinsync.settlement.repository.VendorRepository;
import com.moveinsync.settlement.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconciliationIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Autowired
    private ReconciliationRecordRepository reconciliationRecordRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorRepository vendorRepository;

    private Vendor vendor;
    private SettlementRun settlementRun;

    @BeforeEach
    void setUp() {
        vendor = vendorRepository.findById(1L).orElseThrow();
        settlementRun = settlementRunRepository.findById(1L).orElseGet(() ->
                settlementRunRepository.save(SettlementRun.builder()
                        .vendor(vendor)
                        .periodStart(LocalDate.of(2026, 10, 1))
                        .periodEnd(LocalDate.of(2026, 10, 31))
                        .runType(SettlementRun.RunType.ORIGINAL)
                        .status(SettlementRun.RunStatus.FINALIZED)
                        .idempotencyKey("RECON-KEY-" + UUID.randomUUID())
                        .totalGross(new BigDecimal("70000.00"))
                        .totalNet(new BigDecimal("64775.00"))
                        .tripCount(230)
                        .driverCount(2)
                        .build())
        );
    }

    @Test
    @DisplayName("Should MATCH invoice when variance is within configured tolerance (diff <= tolerance)")
    void testReconciliationWithinToleranceMatches() {
        String invoiceRef = "INV-MATCH-" + UUID.randomUUID().toString().substring(0, 8);
        // Variance is ₹20 (64,795 - 64,775), tolerance is ₹50
        VendorInvoiceSubmitRequestDto submitDto = VendorInvoiceSubmitRequestDto.builder()
                .vendorId(vendor.getId())
                .businessUnitId(1L)
                .invoiceRef(invoiceRef)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .amount(new BigDecimal("64795.00"))
                .currency("INR")
                .build();

        VendorInvoiceResponseDto invoice = reconciliationService.submitInvoice(submitDto);
        assertThat(invoice.getStatus()).isEqualTo(VendorInvoice.InvoiceStatus.PENDING);

        ReconcileRequestDto reconcileDto = ReconcileRequestDto.builder()
                .invoiceId(invoice.getId())
                .settlementRunId(settlementRun.getId())
                .tolerance(new BigDecimal("50.00"))
                .accountingBasis("NET_DRIVER_PAYOUT")
                .build();

        ReconciliationResponseDto result = reconciliationService.reconcileInvoice(reconcileDto, "FINANCE_AUDITOR");

        assertThat(result.getStatus()).isEqualTo(ReconciliationRecord.ReconciliationStatus.MATCHED);
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.getNotes()).contains("matched within acceptable tolerance");

        // Verify invoice entity transitioned to MATCHED
        VendorInvoice updatedInvoice = vendorInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(VendorInvoice.InvoiceStatus.MATCHED);
    }

    @Test
    @DisplayName("Should flag DISCREPANCY when variance exceeds tolerance (diff > tolerance)")
    void testReconciliationExceedingToleranceFlagsDiscrepancy() {
        String invoiceRef = "INV-DISC-" + UUID.randomUUID().toString().substring(0, 8);
        // Variance is ₹725 (65,500 - 64,775), tolerance is ₹50
        VendorInvoiceSubmitRequestDto submitDto = VendorInvoiceSubmitRequestDto.builder()
                .vendorId(vendor.getId())
                .businessUnitId(1L)
                .invoiceRef(invoiceRef)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .amount(new BigDecimal("65500.00"))
                .currency("INR")
                .build();

        VendorInvoiceResponseDto invoice = reconciliationService.submitInvoice(submitDto);

        ReconcileRequestDto reconcileDto = ReconcileRequestDto.builder()
                .invoiceId(invoice.getId())
                .settlementRunId(settlementRun.getId())
                .tolerance(new BigDecimal("50.00"))
                .accountingBasis("NET_DRIVER_PAYOUT")
                .build();

        ReconciliationResponseDto result = reconciliationService.reconcileInvoice(reconcileDto, "FINANCE_AUDITOR");

        assertThat(result.getStatus()).isEqualTo(ReconciliationRecord.ReconciliationStatus.DISCREPANCY);
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("725.00"));
        assertThat(result.getNotes()).contains("variance exceeds threshold");

        // Verify invoice entity transitioned to DISCREPANCY
        VendorInvoice updatedInvoice = vendorInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(VendorInvoice.InvoiceStatus.DISCREPANCY);
    }
}

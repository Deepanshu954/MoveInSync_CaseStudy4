package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Three-way financial reconciliation audit comparing calculated runs, vendor invoices, and payouts.
 *
 * <p>Calculates variance deltas and verifies tolerance limits (e.g. 0.5% variance threshold)
 * before releasing final disbursement batches.
 *
 * @author MoveInSync Core Settlement Team
 */
@Entity
@Table(name = "reconciliation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private VendorInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id")
    private SettlementRun settlementRun;

    @Column(name = "calculated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal calculatedAmount;

    @Column(name = "invoice_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tolerance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(name = "accounting_basis", nullable = false, length = 50)
    @Builder.Default
    private String accountingBasis = "NET_DRIVER_PAYOUT";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum ReconciliationStatus {
        MATCHED, DISCREPANCY, UNDER_REVIEW, RESOLVED
    }
}

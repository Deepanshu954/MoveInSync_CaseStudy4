package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation")
/**
 * Three-way financial reconciliation audit comparing calculated runs, vendor invoices, and payouts.
 *
 * <p>Calculates variance deltas and verifies tolerance limits (e.g. 0.5% variance threshold)
 * before releasing final disbursement batches.
 *
 * @author MoveInSync Core Settlement Team
 */
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
    private String accountingBasis = "NET_DRIVER_PAYOUT";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum ReconciliationStatus {
        MATCHED, DISCREPANCY, UNDER_REVIEW, RESOLVED
    }

    public ReconciliationRecord() {}

    public ReconciliationRecord(Long id, VendorInvoice invoice, SettlementRun settlementRun, BigDecimal calculatedAmount, BigDecimal invoiceAmount, BigDecimal difference, BigDecimal tolerance, ReconciliationStatus status, String accountingBasis, String notes, String resolvedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.invoice = invoice;
        this.settlementRun = settlementRun;
        this.calculatedAmount = calculatedAmount;
        this.invoiceAmount = invoiceAmount;
        this.difference = difference;
        this.tolerance = tolerance;
        this.status = status;
        this.accountingBasis = accountingBasis;
        this.notes = notes;
        this.resolvedBy = resolvedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VendorInvoice getInvoice() {
        return invoice;
    }

    public void setInvoice(VendorInvoice invoice) {
        this.invoice = invoice;
    }

    public SettlementRun getSettlementRun() {
        return settlementRun;
    }

    public void setSettlementRun(SettlementRun settlementRun) {
        this.settlementRun = settlementRun;
    }

    public BigDecimal getCalculatedAmount() {
        return calculatedAmount;
    }

    public void setCalculatedAmount(BigDecimal calculatedAmount) {
        this.calculatedAmount = calculatedAmount;
    }

    public BigDecimal getInvoiceAmount() {
        return invoiceAmount;
    }

    public void setInvoiceAmount(BigDecimal invoiceAmount) {
        this.invoiceAmount = invoiceAmount;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public String getAccountingBasis() {
        return accountingBasis;
    }

    public void setAccountingBasis(String accountingBasis) {
        this.accountingBasis = accountingBasis;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

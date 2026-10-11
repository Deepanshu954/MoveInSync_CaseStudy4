package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settlement_adjustment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"dispute_id", "version"})
})
/**
 * Retroactive delta adjustment correcting a prior finalized settlement run.
 *
 * <p>When a billing dispute ticket is approved by operations after the parent monthly
 * run was already closed and paid, an adjustment is booked into the subsequent billing cycle
 * to disburse owed compensation or recover overpayments without modifying historical ledgers.
 *
 * @author MoveInSync Core Settlement Team
 */
public class SettlementAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_run_id", nullable = false)
    private SettlementRun originalRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_run_id")
    private SettlementRun adjustmentRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    @Column(name = "original_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "revised_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal revisedAmount;

    @Column(name = "delta_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaAmount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SettlementAdjustment() {}

    public SettlementAdjustment(Long id, Dispute dispute, SettlementRun originalRun, SettlementRun adjustmentRun, Driver driver, String lineType, BigDecimal originalAmount, BigDecimal revisedAmount, BigDecimal deltaAmount, String reason, Integer version, Instant createdAt) {
        this.id = id;
        this.dispute = dispute;
        this.originalRun = originalRun;
        this.adjustmentRun = adjustmentRun;
        this.driver = driver;
        this.lineType = lineType;
        this.originalAmount = originalAmount;
        this.revisedAmount = revisedAmount;
        this.deltaAmount = deltaAmount;
        this.reason = reason;
        this.version = version;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public void setDispute(Dispute dispute) {
        this.dispute = dispute;
    }

    public SettlementRun getOriginalRun() {
        return originalRun;
    }

    public void setOriginalRun(SettlementRun originalRun) {
        this.originalRun = originalRun;
    }

    public SettlementRun getAdjustmentRun() {
        return adjustmentRun;
    }

    public void setAdjustmentRun(SettlementRun adjustmentRun) {
        this.adjustmentRun = adjustmentRun;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getRevisedAmount() {
        return revisedAmount;
    }

    public void setRevisedAmount(BigDecimal revisedAmount) {
        this.revisedAmount = revisedAmount;
    }

    public BigDecimal getDeltaAmount() {
        return deltaAmount;
    }

    public void setDeltaAmount(BigDecimal deltaAmount) {
        this.deltaAmount = deltaAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

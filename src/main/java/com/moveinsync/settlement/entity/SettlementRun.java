package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.common.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a monthly settlement calculation run for a vendor and business unit.
 * Tracks total gross, incentives, penalties, tax, and net amounts across the billing period,
 * and maintains the lifecycle status (PENDING -> CALCULATED -> UNDER_REVIEW -> APPROVED -> FINALIZED).
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "settlement_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    @Builder.Default
    private RunType runType = RunType.ORIGINAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "total_gross", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_incentives", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalIncentives = BigDecimal.ZERO;

    @Column(name = "total_penalties", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPenalties = BigDecimal.ZERO;

    @Column(name = "total_tds", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTds = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "trip_count", nullable = false)
    @Builder.Default
    private Integer tripCount = 0;

    @Column(name = "driver_count", nullable = false)
    @Builder.Default
    private Integer driverCount = 0;

    @Column(name = "calculation_version", length = 50)
    private String calculationVersion;

    @Column(name = "config_hash", length = 64)
    private String configHash;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "finalized_by", length = 100)
    private String finalizedBy;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Domain state transition: moves run to UNDER_REVIEW.
     */
    public void moveToReview(String reviewedBy) {
        if (this.status != RunStatus.CALCULATED) {
            throw new InvalidStateTransitionException(
                    "Cannot move to UNDER_REVIEW from status: " + this.status + ". Must be CALCULATED."
            );
        }
        this.status = RunStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    /**
     * Domain state transition: approves the calculated settlement run.
     */
    public void approve(String approver) {
        if (this.status != RunStatus.CALCULATED && this.status != RunStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Cannot approve SettlementRun in status: " + this.status + ". Must be CALCULATED or UNDER_REVIEW."
            );
        }
        this.status = RunStatus.APPROVED;
        this.approvedBy = (approver != null && !approver.isBlank()) ? approver : "FINANCE_DIRECTOR";
        this.updatedAt = Instant.now();
    }

    /**
     * Domain state transition: finalizes the approved settlement run for payout.
     */
    public void finalizeRun(String finalizer) {
        if (this.status != RunStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "Cannot finalize SettlementRun in status: " + this.status + ". Must be APPROVED first."
            );
        }
        this.status = RunStatus.FINALIZED;
        this.finalizedBy = (finalizer != null && !finalizer.isBlank()) ? finalizer : "FINANCE_ADMIN";
        this.updatedAt = Instant.now();
    }

    /**
     * Domain state transition: marks calculation run as failed.
     */
    public void fail(String error) {
        this.status = RunStatus.FAILED;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
    }

    public enum RunType {
        ORIGINAL, ADJUSTMENT
    }

    public enum RunStatus {
        PENDING,
        PROCESSING,
        CALCULATED,
        UNDER_REVIEW,
        APPROVED,
        FINALIZED,
        FAILED
    }
}

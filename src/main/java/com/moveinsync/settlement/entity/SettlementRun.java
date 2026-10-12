package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.common.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a single monthly settlement calculation run for a vendor and business unit.
 *
 * <p>A run aggregates every driver's base pay, incentives, penalties, and TDS deductions
 * for a given billing period into signed line items and payslips, then drives the payout workflow.
 *
 * <h3>Lifecycle State Machine:</h3>
 * <pre>
 *  PENDING --&gt; CALCULATED --&gt; UNDER_REVIEW --&gt; APPROVED --&gt; FINALIZED
 *     |                                                          |
 *     +----------------------------------------------&gt; FAILED &lt;-+
 *                                                   (crash/error)
 * </pre>
 *
 * <h3>Financial Invariant (enforced at FINALIZE):</h3>
 * <pre>
 *  totalNet == totalGross + totalIncentives - totalPenalties - totalTds
 *  |run.totalNet - sum(payslip.netPay)| &lt;= 0.01  (1 paisa tolerance)
 * </pre>
 *
 * <h3>Idempotency:</h3>
 * A partial unique index on (vendor_id, business_unit_id, period_start, period_end)
 * where run_type = 'ORIGINAL' AND status != 'FAILED' prevents duplicate active runs
 * at the database level -- the second tier of the two-tier idempotency strategy.
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

    // SHA-256 hash of the original calculate request payload.
    // Stored by IdempotencyService to detect replayed requests
    // with a different payload under the same Idempotency-Key header.
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

    // Engine version stamped at calculation time (e.g. "1.0.0").
    // Allows retrospective debugging if calculator logic changes between runs.
    @Column(name = "calculation_version", length = 50)
    private String calculationVersion;

    // SHA-256 hash of the rate/policy configuration snapshot used during this run.
    // Auditors can verify that the exact same policy set was applied to all drivers.
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
     * State transition: CALCULATED to UNDER_REVIEW.
     *
     * <p>Finance team members call this to signal that the calculated run
     * is being reviewed for correctness before formal approval.
     *
     * @param reviewedBy username of the finance team member initiating the review
     */
    public void moveToReview(String reviewedBy) {
        if (this.status != RunStatus.CALCULATED) {
            throw new InvalidStateTransitionException(
                    "Cannot move to UNDER_REVIEW from status: " + this.status +
                    ". Run must be CALCULATED first.");
        }
        this.status = RunStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    /**
     * State transition: CALCULATED or UNDER_REVIEW to APPROVED.
     *
     * <p>An authorised approver confirms the financial figures are correct.
     * Both CALCULATED and UNDER_REVIEW are valid source states -- this allows
     * single-step approval without a mandatory review gate for smaller vendors.
     *
     * @param approver username of the authorised finance approver
     */
    public void approve(String approver) {
        if (this.status != RunStatus.CALCULATED && this.status != RunStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Cannot approve SettlementRun in status: " + this.status +
                    ". Run must be CALCULATED or UNDER_REVIEW.");
        }
        this.status = RunStatus.APPROVED;
        this.approvedBy = (approver != null && !approver.isBlank()) ? approver : "FINANCE_DIRECTOR";
        this.updatedAt = Instant.now();
    }

    /**
     * State transition: APPROVED to FINALIZED.
     *
     * <p>Finalization is one-way and irreversible. Once called, SettlementService
     * generates a PayoutInstruction per driver and publishes SETTLEMENT_FINALIZED
     * and PAYOUT_INITIATED events atomically to the Transactional Outbox -- all
     * within the same database transaction.
     *
     * <p>The FinancialInvariantValidator is called just before this transition
     * to guarantee zero monetary drift before any money leaves the system.
     *
     * @param finalizer username of the finance admin triggering disbursement
     */
    public void finalizeRun(String finalizer) {
        if (this.status != RunStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "Cannot finalize SettlementRun in status: " + this.status +
                    ". Run must be APPROVED before finalization.");
        }
        this.status = RunStatus.FINALIZED;
        this.finalizedBy = (finalizer != null && !finalizer.isBlank()) ? finalizer : "FINANCE_ADMIN";
        this.updatedAt = Instant.now();
    }

    /**
     * State transition: ANY → FAILED.
     *
     * <p>Called by {@code SettlementService} on unexpected exceptions during calculation,
     * and by {@code StaleRunRecoveryTask} to recover orphaned PROCESSING runs after
     * a JVM crash or container restart. Marking a run FAILED releases the partial
     * unique index, allowing a fresh billing run to start for the same period.
     *
     * @param error human-readable description of the failure cause
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

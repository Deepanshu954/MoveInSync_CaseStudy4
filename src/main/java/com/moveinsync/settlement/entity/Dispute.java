package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.common.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "dispute")
/**
 * Contested trip billing or deduction ticket filed by a vendor or driver.
 *
 * <p>Tracks claimed monetary difference, supporting dispute evidence, operational comments,
 * and review lifecycle states:
 * <pre>
 *   OPEN -> UNDER_REVIEW -> APPROVED (or REJECTED)
 * </pre>
 *
 * @author MoveInSync Core Settlement Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id")
    private SettlementRun settlementRun;

    @Column(name = "raised_by", nullable = false, length = 100)
    private String raisedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence_ref", length = 500)
    private String evidenceRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void approve(String approver, String resolutionComments) {
        if (this.status != DisputeStatus.OPEN && this.status != DisputeStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("Cannot approve dispute in status: " + this.status);
        }
        this.status = DisputeStatus.APPROVED;
        this.approvedBy = (approver != null && !approver.isBlank()) ? approver : "APPROVER";
        this.resolution = resolutionComments != null ? resolutionComments : "Dispute approved";
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reject(String approver, String resolutionComments) {
        if (this.status != DisputeStatus.OPEN && this.status != DisputeStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("Cannot reject dispute in status: " + this.status);
        }
        this.status = DisputeStatus.REJECTED;
        this.approvedBy = (approver != null && !approver.isBlank()) ? approver : "APPROVER";
        this.resolution = resolutionComments != null ? resolutionComments : "Dispute rejected";
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markUnderReview() {
        if (this.status != DisputeStatus.OPEN) {
            throw new InvalidStateTransitionException("Cannot mark dispute under review from status: " + this.status);
        }
        this.status = DisputeStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    public enum DisputeStatus {
        OPEN, UNDER_REVIEW, APPROVED, REJECTED, RESOLVED
    }
}

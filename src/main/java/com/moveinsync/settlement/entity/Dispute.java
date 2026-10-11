package com.moveinsync.settlement.entity;

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
 *   OPEN -> UNDER_REVIEW -> RESOLVED (or REJECTED)
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

    public enum DisputeStatus {
        OPEN, UNDER_REVIEW, APPROVED, REJECTED, RESOLVED
    }
}

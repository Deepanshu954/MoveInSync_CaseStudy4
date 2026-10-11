package com.moveinsync.settlement.dispute.entity;

import com.moveinsync.settlement.settlement.entity.SettlementRun;
import com.moveinsync.settlement.trip.entity.Trip;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dispute")
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
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum DisputeStatus {
        OPEN, UNDER_REVIEW, APPROVED, REJECTED, RESOLVED
    }

    public Dispute() {}

    public Dispute(Long id, Trip trip, SettlementRun settlementRun, String raisedBy, String reason, String evidenceRef, DisputeStatus status, String resolution, String approvedBy, Instant resolvedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.trip = trip;
        this.settlementRun = settlementRun;
        this.raisedBy = raisedBy;
        this.reason = reason;
        this.evidenceRef = evidenceRef;
        this.status = status;
        this.resolution = resolution;
        this.approvedBy = approvedBy;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public SettlementRun getSettlementRun() {
        return settlementRun;
    }

    public void setSettlementRun(SettlementRun settlementRun) {
        this.settlementRun = settlementRun;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public void setRaisedBy(String raisedBy) {
        this.raisedBy = raisedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public void setEvidenceRef(String evidenceRef) {
        this.evidenceRef = evidenceRef;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
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

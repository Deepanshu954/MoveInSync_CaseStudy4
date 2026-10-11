package com.moveinsync.settlement.trip.entity;

import com.moveinsync.settlement.policy.entity.PenaltyPolicy;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_violation", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "violation_type", "source_ref"})
})
public class TripViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 50)
    private PenaltyPolicy.ViolationType violationType;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ViolationStatus status = ViolationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum ViolationStatus {
        ACTIVE, DISMISSED
    }

    public TripViolation() {}

    public TripViolation(Long id, Trip trip, Instant eventAt, String sourceRef, ViolationStatus status, Instant createdAt) {
        this.id = id;
        this.trip = trip;
        this.eventAt = eventAt;
        this.sourceRef = sourceRef;
        this.status = status;
        this.createdAt = createdAt;
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

    public Instant getEventAt() {
        return eventAt;
    }

    public void setEventAt(Instant eventAt) {
        this.eventAt = eventAt;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public ViolationStatus getStatus() {
        return status;
    }

    public void setStatus(ViolationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

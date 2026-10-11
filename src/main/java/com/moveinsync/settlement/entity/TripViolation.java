package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_violation", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "violation_type", "source_ref"})
})
/**
 * Telematics safety or SLA breach incident recorded against a commute trip.
 *
 * <p>Incident classifications:
 * <ul>
 *   <li>{@code OVER_SPEEDING}: Vehicle exceeded maximum speed limits (e.g. > 60 km/h)</li>
 *   <li>{@code ROUTE_DEVIATION}: Driver deviated from company pre-approved geofenced route</li>
 *   <li>{@code ESCORT_ABSENT}: Night commute lacking mandatory security guard escort</li>
 *   <li>{@code PANIC_ALARM}: Passenger or driver SOS button trigger</li>
 *   <li>{@code DELAYED_PICKUP}: Excessive pickup punctuality breach (> 15 minutes)</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
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

        public TripViolation(Long id, Trip trip, PenaltyPolicy.ViolationType violationType, Instant eventAt, String sourceRef, ViolationStatus status, Instant createdAt) {
        this.id = id;
        this.trip = trip;
        this.violationType = violationType;
        this.eventAt = eventAt;
        this.sourceRef = sourceRef;
        this.status = status;
        this.createdAt = createdAt;
    }

    public TripViolation(Long id, Trip trip, Instant eventAt, String sourceRef, ViolationStatus status, Instant createdAt) {
        this.id = id;
        this.trip = trip;
        this.eventAt = eventAt;
        this.sourceRef = sourceRef;
        this.status = status;
        this.createdAt = createdAt;
    }

    
    public PenaltyPolicy.ViolationType getViolationType() {
        return violationType;
    }

    public void setViolationType(PenaltyPolicy.ViolationType violationType) {
        this.violationType = violationType;
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

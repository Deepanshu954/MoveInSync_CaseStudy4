package com.moveinsync.settlement.trip.entity;

import com.moveinsync.settlement.policy.entity.PenaltyPolicy;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "trip_violation", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "violation_type", "source_ref"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
    private ViolationStatus status = ViolationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum ViolationStatus {
        ACTIVE, DISMISSED
    }
}

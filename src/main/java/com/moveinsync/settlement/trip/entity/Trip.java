package com.moveinsync.settlement.trip.entity;

import com.moveinsync.settlement.contract.entity.Contract;
import com.moveinsync.settlement.driver.entity.Driver;
import com.moveinsync.settlement.vendor.entity.BusinessUnit;
import com.moveinsync.settlement.vendor.entity.Vendor;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "trip", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"vendor_id", "external_ref"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 100)
    private String externalRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "duty_type", nullable = false, length = 30)
    private DutyType dutyType;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "is_on_time", nullable = false)
    @Builder.Default
    private Boolean isOnTime = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TripStatus status = TripStatus.COMPLETED;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum DutyType {
        TRIP, DUTY
    }

    public enum TripStatus {
        COMPLETED, CANCELLED, DISPUTED
    }
}

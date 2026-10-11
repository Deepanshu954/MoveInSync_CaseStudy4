package com.moveinsync.settlement.driver.entity;

import com.moveinsync.settlement.auth.entity.AppUser;
import com.moveinsync.settlement.vendor.entity.BusinessUnit;
import com.moveinsync.settlement.vendor.entity.Vendor;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "driver", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"vendor_id", "external_ref"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @Column(name = "external_ref", nullable = false, length = 100)
    private String externalRef;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DriverStatus status = DriverStatus.ACTIVE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    private AppUser appUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum DriverStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
}

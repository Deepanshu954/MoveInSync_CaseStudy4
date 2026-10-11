package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Fleet transporter organization contracted to provide corporate employee commute transport.
 *
 * <p>Stores legal business profile, GSTIN registration, PAN number, and banking details.
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "vendor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VendorStatus status = VendorStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum VendorStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
}

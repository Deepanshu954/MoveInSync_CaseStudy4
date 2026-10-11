package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "contract")
/**
 * Master commercial contract between MoveInSync / enterprise client and a transport vendor.
 *
 * <p>Defines the legal commercial framework under which a vendor operates vehicle fleets.
 * Each contract specifies:
 * <ul>
 *   <li>The contracting vendor organization and optional business unit scoping</li>
 *   <li>Active commercial currency (default: INR)</li>
 *   <li>Operational status (ACTIVE / INACTIVE)</li>
 *   <li>Time-sliced rate amendments governed via {@link ContractRate}</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum ContractStatus {
        ACTIVE, INACTIVE
    }
}

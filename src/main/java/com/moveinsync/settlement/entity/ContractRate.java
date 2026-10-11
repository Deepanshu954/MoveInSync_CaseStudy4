package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Time-sliced billing rate defined under a vendor contract.
 *
 * <p>Supports mid-month contract amendments (e.g. Oct 15th rate revisions) via temporal ranges:
 * <ul>
 *   <li>{@code effectiveFrom}: inclusive start timestamp (UTC)</li>
 *   <li>{@code effectiveTo}: exclusive end timestamp (UTC, or null if open-ended/active)</li>
 * </ul>
 *
 * <p>Component classifications:
 * <ul>
 *   <li>{@code PER_TRIP}: fixed flat fare per completed trip</li>
 *   <li>{@code PER_DUTY}: duty shift or vehicle day-rate billing</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Entity
@Table(name = "contract_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private ComponentType componentType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo; // NULL = open-ended

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum ComponentType {
        PER_TRIP, PER_DUTY
    }

    public boolean isEffectiveAt(Instant timestamp) {
        if (timestamp == null) return false;
        boolean afterOrAtStart = !timestamp.isBefore(effectiveFrom);
        boolean beforeEnd = (effectiveTo == null) || timestamp.isBefore(effectiveTo);
        return afterOrAtStart && beforeEnd;
    }
}

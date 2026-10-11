package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safety violation and SLA breach penalty deduction policy.
 *
 * <p>Specifies financial deduction amounts for infraction types along with monthly frequency caps
 * to prevent disproportionate driver indebtedness.
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "penalty_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PenaltyPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 50)
    private ViolationType violationType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_window", nullable = false, length = 20)
    private FrequencyWindow frequencyWindow;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences; // NULL = unlimited

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum ViolationType {
        OVER_SPEED,
        ROUTE_DEVIATION,
        NIGHT_SAFETY_BREACH
    }

    public enum FrequencyWindow {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}

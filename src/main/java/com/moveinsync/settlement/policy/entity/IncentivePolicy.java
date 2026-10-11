package com.moveinsync.settlement.policy.entity;

import com.moveinsync.settlement.vendor.entity.BusinessUnit;
import com.moveinsync.settlement.vendor.entity.Vendor;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "incentive_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncentivePolicy {

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
    @Column(nullable = false, length = 50)
    private IncentiveType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "threshold_value")
    private Integer thresholdValue;

    @Column(name = "threshold_percent", precision = 5, scale = 2)
    private BigDecimal thresholdPercent;

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

    public enum IncentiveType {
        ON_TIME_BONUS,
        ATTENDANCE_BONUS,
        WORKED_DAYS_BONUS
    }
}

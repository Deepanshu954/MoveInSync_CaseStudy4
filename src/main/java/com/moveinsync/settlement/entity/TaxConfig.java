package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Statutory Indian Income Tax TDS (Tax Deducted at Source) configuration under Section 194C.
 *
 * <p>Statutory Rates:
 * <ul>
 *   <li>1.0% for Individual / HUF transport contractors with valid PAN</li>
 *   <li>2.0% for Company / Partnership transport firms with valid PAN</li>
 *   <li>20.0% higher deduction under Section 206AA if PAN is missing or invalid</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Entity
@Table(name = "tax_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "monthly_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyThreshold;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 4)
    private BigDecimal ratePercent;

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
}

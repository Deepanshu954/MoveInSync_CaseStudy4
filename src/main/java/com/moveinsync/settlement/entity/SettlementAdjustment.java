package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;



/**
 * Retroactive delta adjustment correcting a prior finalized settlement run.
 *
 * <p>When a billing dispute ticket is approved by operations after the parent monthly
 * run was already closed and paid, an adjustment is booked into the subsequent billing cycle
 * to disburse owed compensation or recover overpayments without modifying historical ledgers.
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "settlement_adjustment", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"dispute_id", "version"})
})

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_run_id", nullable = false)
    private SettlementRun originalRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_run_id")
    private SettlementRun adjustmentRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    @Column(name = "original_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "revised_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal revisedAmount;

    @Column(name = "delta_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal deltaAmount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

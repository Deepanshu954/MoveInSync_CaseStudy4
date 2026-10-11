package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;



/**
 * Atomic financial ledger entry produced during a settlement calculation run.
 *
 * <p>Line types include:
 * <ul>
 *   <li>{@code BASE_PAY}: Base trip earnings derived from contract pricing rules</li>
 *   <li>{@code INCENTIVE}: Volume bonus, punctuality reward, or fuel allowance addition</li>
 *   <li>{@code PENALTY}: Safety violation deduction, unexcused delay, or SLA breach deduction</li>
 *   <li>{@code TDS_DEDUCTION}: Statutory Section 194C withholding tax credit</li>
 *   <li>{@code ADJUSTMENT}: Dispute resolution delta credit or debit</li>
 * </ul>
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "settlement_line", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"run_id", "driver_id", "line_type", "source_type", "source_id"})
})

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private SettlementRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 30)
    private LineType lineType;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "rate_version")
    private Integer rateVersion;

    @Column(name = "policy_version")
    private Integer policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_details")
    private Map<String, Object> calculationDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum LineType {
        BASE_PAY,
        INCENTIVE,
        PENALTY,
        TDS
    }
}

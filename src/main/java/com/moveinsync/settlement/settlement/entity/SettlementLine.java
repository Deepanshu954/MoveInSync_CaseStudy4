package com.moveinsync.settlement.settlement.entity;

import com.moveinsync.settlement.driver.entity.Driver;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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

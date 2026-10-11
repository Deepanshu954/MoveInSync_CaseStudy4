package com.moveinsync.settlement.settlement.entity;

import com.moveinsync.settlement.driver.entity.Driver;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "settlement_line", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"run_id", "driver_id", "line_type", "source_type", "source_id"})
})
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
    private Instant createdAt = Instant.now();

    public enum LineType {
        BASE_PAY,
        INCENTIVE,
        PENALTY,
        TDS
    }

    public SettlementLine() {}

    public SettlementLine(Long id, SettlementRun run, Driver driver, LineType lineType, String sourceType, Long sourceId, String description, BigDecimal amount, Integer rateVersion, Integer policyVersion, Map<String, Object> calculationDetails, Instant createdAt) {
        this.id = id;
        this.run = run;
        this.driver = driver;
        this.lineType = lineType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
        this.amount = amount;
        this.rateVersion = rateVersion;
        this.policyVersion = policyVersion;
        this.calculationDetails = calculationDetails;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SettlementRun getRun() {
        return run;
    }

    public void setRun(SettlementRun run) {
        this.run = run;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public LineType getLineType() {
        return lineType;
    }

    public void setLineType(LineType lineType) {
        this.lineType = lineType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getRateVersion() {
        return rateVersion;
    }

    public void setRateVersion(Integer rateVersion) {
        this.rateVersion = rateVersion;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer policyVersion) {
        this.policyVersion = policyVersion;
    }

    public Map<String, Object> getCalculationDetails() {
        return calculationDetails;
    }

    public void setCalculationDetails(Map<String, Object> calculationDetails) {
        this.calculationDetails = calculationDetails;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

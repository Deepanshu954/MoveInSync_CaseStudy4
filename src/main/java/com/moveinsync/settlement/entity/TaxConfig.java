package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tax_config")
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
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TaxConfig() {}

    public TaxConfig(Long id, String name, BigDecimal monthlyThreshold, BigDecimal ratePercent, Instant effectiveFrom, Instant effectiveTo, Integer version, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.monthlyThreshold = monthlyThreshold;
        this.ratePercent = ratePercent;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.version = version;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMonthlyThreshold() {
        return monthlyThreshold;
    }

    public void setMonthlyThreshold(BigDecimal monthlyThreshold) {
        this.monthlyThreshold = monthlyThreshold;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

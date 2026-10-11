package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "penalty_policy")
/**
 * Safety violation and SLA breach penalty deduction policy.
 *
 * <p>Specifies financial deduction amounts for infraction types along with monthly frequency caps
 * to prevent disproportionate driver indebtedness.
 *
 * @author MoveInSync Core Settlement Team
 */
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
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
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

    public PenaltyPolicy() {}

    public PenaltyPolicy(Long id, Vendor vendor, BusinessUnit businessUnit, ViolationType violationType, BigDecimal amount, FrequencyWindow frequencyWindow, Integer maxOccurrences, Instant effectiveFrom, Instant effectiveTo, Integer version, Instant createdAt) {
        this.id = id;
        this.vendor = vendor;
        this.businessUnit = businessUnit;
        this.violationType = violationType;
        this.amount = amount;
        this.frequencyWindow = frequencyWindow;
        this.maxOccurrences = maxOccurrences;
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

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public BusinessUnit getBusinessUnit() {
        return businessUnit;
    }

    public void setBusinessUnit(BusinessUnit businessUnit) {
        this.businessUnit = businessUnit;
    }

    public ViolationType getViolationType() {
        return violationType;
    }

    public void setViolationType(ViolationType violationType) {
        this.violationType = violationType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public FrequencyWindow getFrequencyWindow() {
        return frequencyWindow;
    }

    public void setFrequencyWindow(FrequencyWindow frequencyWindow) {
        this.frequencyWindow = frequencyWindow;
    }

    public Integer getMaxOccurrences() {
        return maxOccurrences;
    }

    public void setMaxOccurrences(Integer maxOccurrences) {
        this.maxOccurrences = maxOccurrences;
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

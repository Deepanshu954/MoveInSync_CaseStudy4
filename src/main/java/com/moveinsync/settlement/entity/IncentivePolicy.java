package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "incentive_policy")
/**
 * Performance incentive policy rewarding drivers and vendors for operational excellence.
 *
 * <p>Supported incentive models:
 * <ul>
 *   <li><b>TRIP_VOLUME:</b> Tiered slab bonuses (e.g. ₹1,000 bonus for > 150 completed trips)</li>
 *   <li><b>ON_TIME_PERCENTAGE:</b> Punctuality bonus for maintaining > 95% on-time arrival rate</li>
 *   <li><b>PEAK_HOURS:</b> Shift allowances for difficult night or early-morning rosters</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
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
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum IncentiveType {
        ON_TIME_BONUS,
        ATTENDANCE_BONUS,
        WORKED_DAYS_BONUS
    }

    public IncentivePolicy() {}

    public IncentivePolicy(Long id, Vendor vendor, BusinessUnit businessUnit, IncentiveType type, BigDecimal amount, Integer thresholdValue, BigDecimal thresholdPercent, Instant effectiveFrom, Instant effectiveTo, Integer version, Instant createdAt) {
        this.id = id;
        this.vendor = vendor;
        this.businessUnit = businessUnit;
        this.type = type;
        this.amount = amount;
        this.thresholdValue = thresholdValue;
        this.thresholdPercent = thresholdPercent;
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

    public IncentiveType getType() {
        return type;
    }

    public void setType(IncentiveType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Integer thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public BigDecimal getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(BigDecimal thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
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

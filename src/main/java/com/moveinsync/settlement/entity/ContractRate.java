package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "contract_rate")
/**
 * Time-sliced billing rate defined under a vendor contract.
 *
 * <p>Supports mid-month contract amendments (e.g. Oct 15th rate revisions) via temporal ranges:
 * <ul>
 *   <li>{@code effectiveFrom}: inclusive start timestamp (UTC)</li>
 *   <li>{@code effectiveTo}: exclusive end timestamp (UTC, or null if open-ended/active)</li>
 * </ul>
 *
 * <p>Component classifications:
 * <ul>
 *   <li>{@code PER_TRIP}: fixed flat fare per completed trip</li>
 *   <li>{@code PER_DUTY}: duty shift or vehicle day-rate billing</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
public class ContractRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 30)
    private ComponentType componentType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo; // NULL = open-ended

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum ComponentType {
        PER_TRIP, PER_DUTY
    }

    public boolean isEffectiveAt(Instant timestamp) {
        if (timestamp == null) return false;
        boolean afterOrAtStart = !timestamp.isBefore(effectiveFrom);
        boolean beforeEnd = (effectiveTo == null) || timestamp.isBefore(effectiveTo);
        return afterOrAtStart && beforeEnd;
    }

    public ContractRate() {}

    public ContractRate(Long id, Contract contract, ComponentType componentType, BigDecimal amount, Instant effectiveFrom, Instant effectiveTo, Integer version, Instant createdAt) {
        this.id = id;
        this.contract = contract;
        this.componentType = componentType;
        this.amount = amount;
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

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

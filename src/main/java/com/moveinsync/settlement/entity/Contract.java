package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "contract")
/**
 * Master commercial contract between MoveInSync / enterprise client and a transport vendor.
 *
 * <p>Defines the legal commercial framework under which a vendor operates vehicle fleets.
 * Each contract specifies:
 * <ul>
 *   <li>The contracting vendor organization and optional business unit scoping</li>
 *   <li>Active commercial currency (default: INR)</li>
 *   <li>Operational status (ACTIVE / INACTIVE)</li>
 *   <li>Time-sliced rate amendments governed via {@link ContractRate}</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum ContractStatus {
        ACTIVE, INACTIVE
    }

    public Contract() {}

    public Contract(Long id, Vendor vendor, BusinessUnit businessUnit, String name, String currency, ContractStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.vendor = vendor;
        this.businessUnit = businessUnit;
        this.name = name;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

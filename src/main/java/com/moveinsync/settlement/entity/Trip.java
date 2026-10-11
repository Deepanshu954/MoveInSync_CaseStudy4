package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"vendor_id", "external_ref"})
})
/**
 * Represents an individual employee commute transit trip.
 *
 * <p>Trips are logged by telematics devices and the MoveInSync driver mobile application.
 * Contains audited GPS distance, route tracking, scheduled vs actual timestamps,
 * vehicle category, and final settlement billing amounts.
 *
 * <p>Trip Lifecycle:
 * <pre>
 *   SCHEDULED -> IN_PROGRESS -> COMPLETED (or CANCELLED, NO_SHOW, DISPUTED)
 * </pre>
 *
 * @author MoveInSync Core Settlement Team
 */
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 100)
    private String externalRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "duty_type", nullable = false, length = 30)
    private DutyType dutyType;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "is_on_time", nullable = false)
    private Boolean isOnTime = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status = TripStatus.COMPLETED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum DutyType {
        TRIP, DUTY
    }

    public enum TripStatus {
        COMPLETED, CANCELLED, DISPUTED
    }

    public Trip() {}

    public Trip(Long id, String externalRef, Driver driver, Vendor vendor, BusinessUnit businessUnit, Contract contract, DutyType dutyType, Instant completedAt, Boolean isOnTime, TripStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.externalRef = externalRef;
        this.driver = driver;
        this.vendor = vendor;
        this.businessUnit = businessUnit;
        this.contract = contract;
        this.dutyType = dutyType;
        this.completedAt = completedAt;
        this.isOnTime = isOnTime;
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

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
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

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public DutyType getDutyType() {
        return dutyType;
    }

    public void setDutyType(DutyType dutyType) {
        this.dutyType = dutyType;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Boolean getIsOnTime() {
        return isOnTime;
    }

    public void setIsOnTime(Boolean isOnTime) {
        this.isOnTime = isOnTime;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
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

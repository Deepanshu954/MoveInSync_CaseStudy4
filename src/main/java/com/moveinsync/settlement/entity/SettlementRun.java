package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "settlement_run")
/**
 * Batch settlement calculation execution run for a designated billing window.
 *
 * <p>Orchestrates monthly vendor and driver financial calculations:
 * <ol>
 *   <li>Gathers eligible completed trips within {@code [periodStart, periodEnd)}</li>
 *   <li>Calculates baseline trip earnings via active time-sliced {@link ContractRate} cards</li>
 *   <li>Evaluates driver performance incentives (volume slabs, on-time percentage)</li>
 *   <li>Calculates safety and SLA breach penalty deductions with monthly caps</li>
 *   <li>Computes Indian statutory TDS under Section 194C (1% or 2%)</li>
 *   <li>Maintains financial balance equation: {@code NetPay = GrossPay + Incentives - Penalties - TDS}</li>
 * </ol>
 *
 * <p>Enforces strict state transitions:
 * <pre>
 *   PENDING -> PROCESSING -> APPROVED -> FINALIZED (or FAILED / REJECTED)
 * </pre>
 *
 * @author MoveInSync Core Settlement Team
 */
public class SettlementRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private RunType runType = RunType.ORIGINAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "total_gross", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_incentives", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIncentives = BigDecimal.ZERO;

    @Column(name = "total_penalties", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPenalties = BigDecimal.ZERO;

    @Column(name = "total_tds", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTds = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "trip_count", nullable = false)
    private Integer tripCount = 0;

    @Column(name = "driver_count", nullable = false)
    private Integer driverCount = 0;

    @Column(name = "calculation_version", length = 50)
    private String calculationVersion;

    @Column(name = "config_hash", length = 64)
    private String configHash;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "finalized_by", length = 100)
    private String finalizedBy;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum RunType {
        ORIGINAL, ADJUSTMENT
    }

    public enum RunStatus {
        PENDING,
        PROCESSING,
        CALCULATED,
        UNDER_REVIEW,
        APPROVED,
        FINALIZED,
        FAILED
    }

    public SettlementRun() {}

    public SettlementRun(Long id, Vendor vendor, BusinessUnit businessUnit, LocalDate periodStart, LocalDate periodEnd, RunType runType, RunStatus status, String idempotencyKey, BigDecimal totalGross, BigDecimal totalIncentives, BigDecimal totalPenalties, BigDecimal totalTds, BigDecimal totalNet, Integer tripCount, Integer driverCount, String calculationVersion, String configHash, String initiatedBy, String approvedBy, String finalizedBy, String errorMessage, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.vendor = vendor;
        this.businessUnit = businessUnit;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.runType = runType;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.totalGross = totalGross;
        this.totalIncentives = totalIncentives;
        this.totalPenalties = totalPenalties;
        this.totalTds = totalTds;
        this.totalNet = totalNet;
        this.tripCount = tripCount;
        this.driverCount = driverCount;
        this.calculationVersion = calculationVersion;
        this.configHash = configHash;
        this.initiatedBy = initiatedBy;
        this.approvedBy = approvedBy;
        this.finalizedBy = finalizedBy;
        this.errorMessage = errorMessage;
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

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public RunType getRunType() {
        return runType;
    }

    public void setRunType(RunType runType) {
        this.runType = runType;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public void setTotalGross(BigDecimal totalGross) {
        this.totalGross = totalGross;
    }

    public BigDecimal getTotalIncentives() {
        return totalIncentives;
    }

    public void setTotalIncentives(BigDecimal totalIncentives) {
        this.totalIncentives = totalIncentives;
    }

    public BigDecimal getTotalPenalties() {
        return totalPenalties;
    }

    public void setTotalPenalties(BigDecimal totalPenalties) {
        this.totalPenalties = totalPenalties;
    }

    public BigDecimal getTotalTds() {
        return totalTds;
    }

    public void setTotalTds(BigDecimal totalTds) {
        this.totalTds = totalTds;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public void setTotalNet(BigDecimal totalNet) {
        this.totalNet = totalNet;
    }

    public Integer getTripCount() {
        return tripCount;
    }

    public void setTripCount(Integer tripCount) {
        this.tripCount = tripCount;
    }

    public Integer getDriverCount() {
        return driverCount;
    }

    public void setDriverCount(Integer driverCount) {
        this.driverCount = driverCount;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }

    public String getConfigHash() {
        return configHash;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getFinalizedBy() {
        return finalizedBy;
    }

    public void setFinalizedBy(String finalizedBy) {
        this.finalizedBy = finalizedBy;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

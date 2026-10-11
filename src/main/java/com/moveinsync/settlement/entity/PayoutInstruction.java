package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payout_instruction")
/**
 * Banking payout instruction generated upon settlement run approval.
 *
 * <p>Dispatched to corporate banking payment rails (RazorpayX, Cashfree, ICICI Direct).
 * Tracks bank account IFSC, beneficiary name, transfer reference (UTR), and disbursement lifecycle.
 *
 * @author MoveInSync Core Settlement Team
 */
public class PayoutInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id", nullable = false)
    private SettlementRun settlementRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum PayoutStatus {
        PENDING, INITIATED, SUCCESS, FAILED, CANCELLED
    }

    public PayoutInstruction() {}

    public PayoutInstruction(Long id, SettlementRun settlementRun, Driver driver, Vendor vendor, BigDecimal amount, String currency, String idempotencyKey, PayoutStatus status, String providerReference, String failureReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.settlementRun = settlementRun;
        this.driver = driver;
        this.vendor = vendor;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.providerReference = providerReference;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SettlementRun getSettlementRun() {
        return settlementRun;
    }

    public void setSettlementRun(SettlementRun settlementRun) {
        this.settlementRun = settlementRun;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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

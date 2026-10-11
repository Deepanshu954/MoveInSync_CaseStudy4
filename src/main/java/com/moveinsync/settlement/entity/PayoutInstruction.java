package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
    private String currency = "INR";

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void markSuccess(String ref) {
        this.status = PayoutStatus.SUCCESS;
        this.providerReference = ref;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public enum PayoutStatus {
        PENDING, INITIATED, SUCCESS, FAILED, CANCELLED
    }
}

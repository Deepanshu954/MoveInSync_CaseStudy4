package com.moveinsync.settlement.invoice.entity;

import com.moveinsync.settlement.vendor.entity.BusinessUnit;
import com.moveinsync.settlement.vendor.entity.Vendor;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vendor_invoice", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"vendor_id", "invoice_ref"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @Column(name = "invoice_ref", nullable = false, length = 100)
    private String invoiceRef;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum InvoiceStatus {
        PENDING, MATCHED, DISCREPANCY, RESOLVED
    }
}

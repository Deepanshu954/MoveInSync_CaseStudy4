package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.VendorInvoice;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorInvoiceResponseDto {
    private Long id;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private Long businessUnitId;
    private String invoiceRef;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String currency;
    private VendorInvoice.InvoiceStatus status;
    private Instant createdAt;

    public static VendorInvoiceResponseDto fromEntity(VendorInvoice v) {
        if (v == null) return null;
        return VendorInvoiceResponseDto.builder()
                .id(v.getId())
                .vendorId(v.getVendor().getId())
                .vendorCode(v.getVendor().getCode())
                .vendorName(v.getVendor().getName())
                .businessUnitId(v.getBusinessUnit() != null ? v.getBusinessUnit().getId() : null)
                .invoiceRef(v.getInvoiceRef())
                .periodStart(v.getPeriodStart())
                .periodEnd(v.getPeriodEnd())
                .amount(v.getAmount())
                .currency(v.getCurrency())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .build();
    }
}

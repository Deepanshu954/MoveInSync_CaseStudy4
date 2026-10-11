package com.moveinsync.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorInvoiceSubmitRequestDto {
    @NotNull
    private Long vendorId;
    private Long businessUnitId;
    @NotBlank
    private String invoiceRef;
    @NotNull
    private LocalDate periodStart;
    @NotNull
    private LocalDate periodEnd;
    @NotNull
    private BigDecimal amount;
    @Builder.Default
    private String currency = "INR";
}

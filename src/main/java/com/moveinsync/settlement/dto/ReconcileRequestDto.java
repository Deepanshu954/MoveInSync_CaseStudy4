package com.moveinsync.settlement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconcileRequestDto {
    @NotNull
    private Long invoiceId;
    @NotNull
    private Long settlementRunId;
    @Builder.Default
    private BigDecimal tolerance = new BigDecimal("50.00");
    private BigDecimal tolerancePercentage;
    @Builder.Default
    private String accountingBasis = "NET_DRIVER_PAYOUT";
}

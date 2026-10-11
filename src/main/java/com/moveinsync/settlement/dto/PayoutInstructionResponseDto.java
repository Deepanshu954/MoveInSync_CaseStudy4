package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.PayoutInstruction;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutInstructionResponseDto {
    private Long id;
    private Long settlementRunId;
    private Long vendorId;
    private Long driverId;
    private String driverName;
    private String driverExternalRef;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
    private PayoutInstruction.PayoutStatus status;
    private String providerReference;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static PayoutInstructionResponseDto fromEntity(PayoutInstruction p) {
        if (p == null) return null;
        return PayoutInstructionResponseDto.builder()
                .id(p.getId())
                .settlementRunId(p.getSettlementRun() != null ? p.getSettlementRun().getId() : null)
                .vendorId(p.getVendor() != null ? p.getVendor().getId() : null)
                .driverId(p.getDriver() != null ? p.getDriver().getId() : null)
                .driverName(p.getDriver() != null ? p.getDriver().getName() : null)
                .driverExternalRef(p.getDriver() != null ? p.getDriver().getExternalRef() : null)
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .idempotencyKey(p.getIdempotencyKey())
                .status(p.getStatus())
                .providerReference(p.getProviderReference())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

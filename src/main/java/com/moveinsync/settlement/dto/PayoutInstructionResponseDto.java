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
}

package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.PayoutInstruction;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankingWebhookRequestDto {
    @NotNull
    private Long payoutId;
    private String providerReference;
    @NotNull
    private PayoutInstruction.PayoutStatus status;
    private String failureReason;
}

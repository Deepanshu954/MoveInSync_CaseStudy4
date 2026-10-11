package com.moveinsync.settlement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResolveResponseDto {
    private DisputeResponseDto dispute;
    private SettlementAdjustmentResponseDto adjustment;
    private PayoutInstructionResponseDto payoutInstruction;
}

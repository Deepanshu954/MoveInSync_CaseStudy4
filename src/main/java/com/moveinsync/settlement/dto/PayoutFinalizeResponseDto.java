package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.SettlementRun;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutFinalizeResponseDto {
    private Long settlementRunId;
    private SettlementRun.RunStatus status;
    private BigDecimal totalDisbursed;
    private int instructionsGenerated;
    private int outboxEventsQueued;
    private String finalizedBy;
    private Instant finalizedAt;
    private List<PayoutInstructionResponseDto> instructions;
}

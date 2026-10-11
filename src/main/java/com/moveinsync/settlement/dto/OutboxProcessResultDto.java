package com.moveinsync.settlement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxProcessResultDto {
    private int totalEvaluated;
    private int processedCount;
    private int failedCount;
    private long pendingRemaining;
}

package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.OutboxEvent;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventResponseDto {
    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payload;
    private OutboxEvent.OutboxStatus status;
    private Integer retryCount;
    private Instant createdAt;
    private Instant processedAt;
}

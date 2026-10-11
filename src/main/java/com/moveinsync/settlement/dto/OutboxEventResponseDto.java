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

    public static OutboxEventResponseDto fromEntity(OutboxEvent e) {
        if (e == null) return null;
        return OutboxEventResponseDto.builder()
                .id(e.getId())
                .aggregateType(e.getAggregateType())
                .aggregateId(e.getAggregateId())
                .eventType(e.getEventType())
                .payload(e.getPayload())
                .status(e.getStatus())
                .retryCount(e.getRetryCount())
                .createdAt(e.getCreatedAt())
                .processedAt(e.getProcessedAt())
                .build();
    }
}

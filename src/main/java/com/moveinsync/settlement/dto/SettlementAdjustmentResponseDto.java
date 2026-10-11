package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.SettlementAdjustment;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAdjustmentResponseDto {
    private Long id;
    private Long disputeId;
    private Long originalRunId;
    private Long driverId;
    private String lineType;
    private BigDecimal originalAmount;
    private BigDecimal revisedAmount;
    private BigDecimal deltaAmount;
    private String reason;
    private Integer version;
    private Instant createdAt;

    public static SettlementAdjustmentResponseDto fromEntity(SettlementAdjustment a) {
        if (a == null) return null;
        return SettlementAdjustmentResponseDto.builder()
                .id(a.getId())
                .disputeId(a.getDispute() != null ? a.getDispute().getId() : null)
                .originalRunId(a.getOriginalRun() != null ? a.getOriginalRun().getId() : null)
                .driverId(a.getDriver() != null ? a.getDriver().getId() : null)
                .lineType(a.getLineType())
                .originalAmount(a.getOriginalAmount())
                .revisedAmount(a.getRevisedAmount())
                .deltaAmount(a.getDeltaAmount())
                .reason(a.getReason())
                .version(a.getVersion())
                .createdAt(a.getCreatedAt())
                .build();
    }
}

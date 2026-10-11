package com.moveinsync.settlement.dto.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementFinalizedPayload {
    private Long runId;
    private Long vendorId;
    private BigDecimal totalNet;
    private Integer driverCount;
    private Integer tripCount;
    private String finalizedBy;
    private Instant finalizedAt;
}

package com.moveinsync.settlement.dto.event;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutInitiatedPayload {
    private Long payoutId;
    private Long runId;
    private Long driverId;
    private Long vendorId;
    private String beneficiaryRef;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
}

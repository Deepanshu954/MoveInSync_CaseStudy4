package com.moveinsync.settlement.payout.gateway;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransferRequest {
    private Long payoutId;
    private String idempotencyKey;
    private BigDecimal amount;
    private String currency;
    private String beneficiaryName;
    private String beneficiaryRef;
}

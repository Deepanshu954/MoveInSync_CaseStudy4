package com.moveinsync.settlement.payout.gateway;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransferResponse {
    private boolean success;
    private String providerReference;
    private String failureReason;
}

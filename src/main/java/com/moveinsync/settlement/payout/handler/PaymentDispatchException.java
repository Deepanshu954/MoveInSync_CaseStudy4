package com.moveinsync.settlement.payout.handler;

import com.moveinsync.settlement.entity.PayoutInstruction;
import lombok.Getter;

/**
 * Domain exception thrown when external payment gateway disbursement fails.
 */
@Getter
public class PaymentDispatchException extends RuntimeException {

    private final PayoutInstruction payout;

    public PaymentDispatchException(String message, PayoutInstruction payout) {
        super(message);
        this.payout = payout;
    }
}

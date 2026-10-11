package com.moveinsync.settlement.payout.gateway;

/**
 * Port contract for corporate banking and disbursement gateways (RazorpayX, ICICI IMPS, Cashfree).
 *
 * @author Deepanshu
 */
public interface PaymentGatewayClient {
    PaymentTransferResponse transferFunds(PaymentTransferRequest request);
}

package com.moveinsync.settlement.payout.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.payout.gateway.PaymentGatewayClient;
import com.moveinsync.settlement.payout.gateway.PaymentTransferRequest;
import com.moveinsync.settlement.payout.gateway.PaymentTransferResponse;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Handles 'PAYOUT_INITIATED' outbox events by invoking the banking payment rails.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PayoutInitiatedEventHandler implements OutboxEventHandler {

    private final PayoutInstructionRepository payoutInstructionRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String eventType) {
        return "PAYOUT_INITIATED".equals(eventType);
    }

    @Override
    public boolean handle(OutboxEvent event) throws Exception {
        Long payoutId = event.getAggregateId();
        PayoutInstruction payout = payoutInstructionRepository.findById(payoutId).orElse(null);

        if (payout == null) {
            log.error("PayoutInstruction not found for aggregateId: {}", payoutId);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());
            return false;
        }

        JsonNode rootNode = objectMapper.readTree(event.getPayload());
        String beneficiaryRef = rootNode.has("beneficiaryRef") ? rootNode.get("beneficiaryRef").asText() : "";
        if (beneficiaryRef.isEmpty() && payout.getDriver() != null) {
            beneficiaryRef = payout.getDriver().getExternalRef();
        }

        PaymentTransferRequest request = PaymentTransferRequest.builder()
                .payoutId(payout.getId())
                .idempotencyKey(payout.getIdempotencyKey())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .beneficiaryName(payout.getDriver() != null ? payout.getDriver().getName() : "VENDOR_SETTLEMENT")
                .beneficiaryRef(beneficiaryRef)
                .build();

        PaymentTransferResponse response = paymentGatewayClient.transferFunds(request);

        if (response.isSuccess()) {
            payout.markSuccess(response.getProviderReference());
            payoutInstructionRepository.save(payout);

            event.markProcessed();
            log.info("Successfully disbursed payout {} with UTR: {}", payoutId, response.getProviderReference());
            return true;
        } else {
            throw new PaymentDispatchException(response.getFailureReason(), payout);
        }
    }
}

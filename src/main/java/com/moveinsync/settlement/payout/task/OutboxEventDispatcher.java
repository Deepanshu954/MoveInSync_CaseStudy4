package com.moveinsync.settlement.payout.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.payout.gateway.PaymentGatewayClient;
import com.moveinsync.settlement.payout.gateway.PaymentTransferRequest;
import com.moveinsync.settlement.payout.gateway.PaymentTransferResponse;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Isolated dispatcher executing single outbox event deliveries within independent transactions.
 *
 * <p>Architectural guarantees:
 * <ul>
 *   <li><b>Fault Isolation:</b> A failure or timeout in one payment does not roll back sibling events.</li>
 *   <li><b>Short-Lived DB Locks:</b> Network I/O is bounded and database locks are quickly released.</li>
 *   <li><b>Automatic Dead-Lettering:</b> Permanently failed events transition to FAILED after 3 retries.</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final PayoutInstructionRepository payoutInstructionRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ObjectMapper objectMapper;

    public static final int MAX_RETRIES = 3;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatchSingleEvent(OutboxEvent event) {
        try {
            if ("PAYOUT_INITIATED".equals(event.getEventType())) {
                return processPayoutInitiated(event);
            } else if ("SETTLEMENT_FINALIZED".equals(event.getEventType())) {
                return processSettlementFinalized(event);
            } else {
                log.warn("Unrecognized outbox event type: {}, marking processed", event.getEventType());
                event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
                return true;
            }
        } catch (Exception ex) {
            log.error("Error dispatching outbox event {}: {}", event.getId(), ex.getMessage(), ex);
            handleFailure(event, null, "DISPATCH_ERROR: " + ex.getMessage());
            return false;
        }
    }

    private boolean processPayoutInitiated(OutboxEvent event) throws Exception {
        Long payoutId = event.getAggregateId();
        PayoutInstruction payout = payoutInstructionRepository.findById(payoutId).orElse(null);

        if (payout == null) {
            log.error("PayoutInstruction not found for aggregateId: {}", payoutId);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
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
            payout.setStatus(PayoutInstruction.PayoutStatus.SUCCESS);
            payout.setProviderReference(response.getProviderReference());
            payout.setUpdatedAt(Instant.now());
            payoutInstructionRepository.save(payout);

            event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);

            log.info("Successfully disbursed payout {} with UTR: {}", payoutId, response.getProviderReference());
            return true;
        } else {
            handleFailure(event, payout, response.getFailureReason());
            return false;
        }
    }

    private boolean processSettlementFinalized(OutboxEvent event) {
        log.info("Settlement run finalized notification published: aggregateId={}", event.getAggregateId());
        event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        outboxEventRepository.save(event);
        return true;
    }

    private void handleFailure(OutboxEvent event, PayoutInstruction payout, String reason) {
        int retries = event.getRetryCount() + 1;
        event.setRetryCount(retries);

        if (retries >= MAX_RETRIES) {
            log.error("DEAD-LETTER ALERT: Outbox event {} reached max retries ({}), marking FAILED. Reason: {}",
                    event.getId(), MAX_RETRIES, reason);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());

            if (payout != null) {
                payout.setStatus(PayoutInstruction.PayoutStatus.FAILED);
                payout.setFailureReason(reason);
                payout.setUpdatedAt(Instant.now());
                payoutInstructionRepository.save(payout);
            }
        } else {
            log.warn("Transient failure for outbox event {} (attempt {}/{}): {}",
                    event.getId(), retries, MAX_RETRIES, reason);
        }

        outboxEventRepository.save(event);
    }
}

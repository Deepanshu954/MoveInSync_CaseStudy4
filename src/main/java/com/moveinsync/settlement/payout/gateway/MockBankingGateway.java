package com.moveinsync.settlement.payout.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic Mock Banking Gateway simulating corporate IMPS/NEFT/UPI rails.
 *
 * <p>Features:
 * <ul>
 *   <li>Simulates banking idempotency (same idempotencyKey returns identical UTR reference)</li>
 *   <li>Simulates provider failure when beneficiary reference contains 'SIMULATE_FAIL'</li>
 *   <li>Emits standard Indian banking UTR format: UTR-YYYYMMDD-HEX</li>
 * </ul>
 *
 * @author Deepanshu
 */
@Service
@Slf4j
public class MockBankingGateway implements PaymentGatewayClient {

    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    @Override
    public PaymentTransferResponse transferFunds(PaymentTransferRequest request) {
        log.info("Dispatching payment to banking gateway: payoutId={}, amount={} {}, idempotencyKey={}",
                request.getPayoutId(), request.getAmount(), request.getCurrency(), request.getIdempotencyKey());

        // Simulated failure trigger for resilience testing
        if (request.getBeneficiaryRef() != null && request.getBeneficiaryRef().contains("SIMULATE_FAIL")) {
            log.warn("Simulated banking rail failure triggered for beneficiary: {}", request.getBeneficiaryRef());
            return PaymentTransferResponse.builder()
                    .success(false)
                    .failureReason("BANK_GATEWAY_TIMEOUT: Beneficiary bank CBS did not acknowledge IMPS packet")
                    .build();
        }

        // Check gateway-level idempotency
        String utr = idempotencyCache.computeIfAbsent(request.getIdempotencyKey(), key -> {
            String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return "UTR-" + datePrefix + "-" + randomSuffix;
        });

        log.info("Banking gateway confirmed transfer: payoutId={}, UTR={}", request.getPayoutId(), utr);
        return PaymentTransferResponse.builder()
                .success(true)
                .providerReference(utr)
                .build();
    }
}

package com.moveinsync.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.OutboxProcessResultDto;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.payout.task.OutboxProcessorTask;
import com.moveinsync.settlement.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxProcessorIntegrationTest {

    @Autowired
    private OutboxProcessorTask outboxProcessorTask;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PayoutInstructionRepository payoutInstructionRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Vendor vendor;
    private Driver driver;
    private SettlementRun settlementRun;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();

        vendor = vendorRepository.findById(1L).orElseThrow();
        driver = driverRepository.findById(1L).orElseThrow();
        BusinessUnit bu = businessUnitRepository.findById(1L).orElse(null);

        settlementRun = settlementRunRepository.save(SettlementRun.builder()
                .vendor(vendor)
                .businessUnit(bu)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .runType(SettlementRun.RunType.ORIGINAL)
                .status(SettlementRun.RunStatus.FINALIZED)
                .idempotencyKey("KEY-OUTBOX-RUN-" + UUID.randomUUID())
                .totalGross(new BigDecimal("1000.00"))
                .totalIncentives(BigDecimal.ZERO)
                .totalPenalties(BigDecimal.ZERO)
                .totalTds(new BigDecimal("10.00"))
                .totalNet(new BigDecimal("990.00"))
                .tripCount(5)
                .driverCount(1)
                .calculationVersion("v1.0.0")
                .initiatedBy("TEST")
                .approvedBy("TEST")
                .finalizedBy("TEST")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();
        if (settlementRun != null && settlementRun.getId() != null) {
            settlementRunRepository.deleteById(settlementRun.getId());
        }
    }

    @Test
    @DisplayName("Should successfully process pending outbox payout events and populate provider UTR")
    void testSuccessfulOutboxProcessing() throws Exception {
        // Create pending PayoutInstruction
        PayoutInstruction payout = payoutInstructionRepository.save(PayoutInstruction.builder()
                .settlementRun(settlementRun)
                .vendor(vendor)
                .driver(driver)
                .amount(new BigDecimal("990.00"))
                .currency("INR")
                .idempotencyKey("PAYOUT-TEST-" + UUID.randomUUID())
                .status(PayoutInstruction.PayoutStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // Create pending OutboxEvent
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("payoutId", payout.getId());
        payloadMap.put("amount", payout.getAmount());
        payloadMap.put("beneficiaryRef", driver.getExternalRef());

        OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("PAYOUT_INSTRUCTION")
                .aggregateId(payout.getId())
                .eventType("PAYOUT_INITIATED")
                .payload(objectMapper.writeValueAsString(payloadMap))
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build());

        // Execute Outbox Worker
        OutboxProcessResultDto result = outboxProcessorTask.processPendingEvents();
        assertThat(result.getProcessedCount()).isGreaterThanOrEqualTo(1);

        // Verify Outbox Event updated
        OutboxEvent updatedEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PROCESSED);
        assertThat(updatedEvent.getProcessedAt()).isNotNull();

        // Verify Payout updated with UTR
        PayoutInstruction updatedPayout = payoutInstructionRepository.findById(payout.getId()).orElseThrow();
        assertThat(updatedPayout.getStatus()).isEqualTo(PayoutInstruction.PayoutStatus.SUCCESS);
        assertThat(updatedPayout.getProviderReference()).startsWith("UTR-");
    }

    @Test
    @DisplayName("Should handle transient failure and dead-letter to FAILED after 3 retries")
    void testDeadLetteringOnRepeatedFailure() throws Exception {
        // Create payout with beneficiaryRef configured to trigger SIMULATE_FAIL
        PayoutInstruction payout = payoutInstructionRepository.save(PayoutInstruction.builder()
                .settlementRun(settlementRun)
                .vendor(vendor)
                .driver(driver)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .idempotencyKey("PAYOUT-FAIL-" + UUID.randomUUID())
                .status(PayoutInstruction.PayoutStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("payoutId", payout.getId());
        payloadMap.put("amount", payout.getAmount());
        payloadMap.put("beneficiaryRef", "SIMULATE_FAIL_GATEWAY_TIMEOUT");

        OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("PAYOUT_INSTRUCTION")
                .aggregateId(payout.getId())
                .eventType("PAYOUT_INITIATED")
                .payload(objectMapper.writeValueAsString(payloadMap))
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build());

        // Attempt 1 -> retry count = 1, status PENDING
        outboxProcessorTask.processPendingEvents();
        OutboxEvent eventAfter1 = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(eventAfter1.getRetryCount()).isEqualTo(1);
        assertThat(eventAfter1.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);

        // Attempt 2 -> retry count = 2, status PENDING
        outboxProcessorTask.processPendingEvents();
        OutboxEvent eventAfter2 = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(eventAfter2.getRetryCount()).isEqualTo(2);
        assertThat(eventAfter2.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);

        // Attempt 3 -> retry count = 3 >= MAX_RETRIES, status FAILED
        outboxProcessorTask.processPendingEvents();
        OutboxEvent eventAfter3 = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(eventAfter3.getRetryCount()).isEqualTo(3);
        assertThat(eventAfter3.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);

        // Payout should also be marked FAILED with reason
        PayoutInstruction failedPayout = payoutInstructionRepository.findById(payout.getId()).orElseThrow();
        assertThat(failedPayout.getStatus()).isEqualTo(PayoutInstruction.PayoutStatus.FAILED);
        assertThat(failedPayout.getFailureReason()).contains("BANK_GATEWAY_TIMEOUT");
    }
}

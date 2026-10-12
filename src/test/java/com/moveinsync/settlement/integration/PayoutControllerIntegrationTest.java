package com.moveinsync.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.BankingWebhookRequestDto;
import com.moveinsync.settlement.entity.BusinessUnit;
import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.repository.BusinessUnitRepository;
import com.moveinsync.settlement.repository.DriverRepository;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorRepository;
import com.moveinsync.settlement.security.CustomUserDetailsService;
import com.moveinsync.settlement.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for PayoutController and Transactional Outbox endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PayoutControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

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

    private String adminToken;
    private SettlementRun run;
    private PayoutInstruction payout;

    @BeforeEach
    void setUp() {
        UserDetails adminUser = userDetailsService.loadUserByUsername("admin");
        adminToken = "Bearer " + jwtService.generateToken(adminUser);

        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();

        Vendor vendor = vendorRepository.findById(1L).orElseThrow();
        Driver driver = driverRepository.findById(1L).orElseThrow();
        BusinessUnit bu = businessUnitRepository.findById(1L).orElse(null);

        run = settlementRunRepository.save(SettlementRun.builder()
                .vendor(vendor)
                .businessUnit(bu)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .runType(SettlementRun.RunType.ORIGINAL)
                .status(SettlementRun.RunStatus.FINALIZED)
                .idempotencyKey("KEY-PAYOUT-CTRL-" + UUID.randomUUID())
                .totalGross(new BigDecimal("1000.00"))
                .totalIncentives(BigDecimal.ZERO)
                .totalPenalties(BigDecimal.ZERO)
                .totalTds(new BigDecimal("10.00"))
                .totalNet(new BigDecimal("990.00"))
                .tripCount(5)
                .driverCount(1)
                .calculationVersion("v1.0.0")
                .initiatedBy("ADMIN")
                .approvedBy("ADMIN")
                .finalizedBy("ADMIN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        payout = payoutInstructionRepository.save(PayoutInstruction.builder()
                .settlementRun(run)
                .vendor(vendor)
                .driver(driver)
                .amount(new BigDecimal("990.00"))
                .currency("INR")
                .idempotencyKey("PAYOUT-REST-" + UUID.randomUUID())
                .status(PayoutInstruction.PayoutStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        payoutInstructionRepository.deleteAll();
        if (run != null && run.getId() != null) {
            settlementRunRepository.deleteById(run.getId());
        }
    }

    @Test
    @DisplayName("GET /api/payouts/{id} - Returns 200 OK with payout details")
    void shouldGetPayoutById() throws Exception {
        mockMvc.perform(get("/api/payouts/" + payout.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(payout.getId().intValue())))
                .andExpect(jsonPath("$.amount", is(990.00)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.driverName", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/payouts/run/{runId} - Returns list of payouts for a run")
    void shouldGetPayoutsByRunId() throws Exception {
        mockMvc.perform(get("/api/payouts/run/" + run.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(payout.getId().intValue())));
    }

    @Test
    @DisplayName("POST /api/payouts/outbox/process - Manually triggers outbox processor")
    void shouldTriggerOutboxProcessor() throws Exception {
        mockMvc.perform(post("/api/payouts/outbox/process")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluated", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.processedCount", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("POST /api/payouts/webhook - Simulates bank callback update")
    void shouldHandleBankingWebhook() throws Exception {
        BankingWebhookRequestDto webhookRequest = BankingWebhookRequestDto.builder()
                .payoutId(payout.getId())
                .status(PayoutInstruction.PayoutStatus.SUCCESS)
                .providerReference("UTR-BANK-WEBHOOK-9999")
                .build();

        mockMvc.perform(post("/api/payouts/webhook")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(payout.getId().intValue())))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.providerReference", is("UTR-BANK-WEBHOOK-9999")));
    }
}

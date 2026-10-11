package com.moveinsync.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.DisputeCreateRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveRequestDto;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.repository.SettlementLineRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.TripRepository;
import com.moveinsync.settlement.security.CustomUserDetailsService;
import com.moveinsync.settlement.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DisputeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private SettlementLineRepository settlementLineRepository;

    private String adminToken;
    private Trip trip;
    private SettlementRun run;

    @BeforeEach
    void setUp() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        adminToken = "Bearer " + jwtService.generateToken(admin);

        trip = tripRepository.findById(1L).orElseThrow();
        trip.setStatus(Trip.TripStatus.COMPLETED);
        tripRepository.save(trip);

        run = settlementRunRepository.findById(1L).orElseGet(() ->
                settlementRunRepository.save(SettlementRun.builder()
                        .vendor(trip.getVendor())
                        .periodStart(LocalDate.of(2026, 10, 1))
                        .periodEnd(LocalDate.of(2026, 10, 31))
                        .idempotencyKey("DISP-CTRL-" + UUID.randomUUID())
                        .totalGross(new BigDecimal("70000.00"))
                        .totalNet(new BigDecimal("64775.00"))
                        .tripCount(230)
                        .driverCount(2)
                        .status(SettlementRun.RunStatus.FINALIZED)
                        .build())
        );

        settlementLineRepository.findFirstBySourceTypeAndSourceId("TRIP", trip.getId())
                .orElseGet(() -> settlementLineRepository.save(SettlementLine.builder()
                        .run(run)
                        .driver(trip.getDriver())
                        .lineType(SettlementLine.LineType.BASE_PAY)
                        .sourceType("TRIP")
                        .sourceId(trip.getId())
                        .description("Trip Fare Baseline")
                        .amount(new BigDecimal("250.00"))
                        .build())
                );
    }

    @Test
    @DisplayName("POST /api/disputes & /{id}/resolve - Complete dispute delta resolution flow")
    void shouldRaiseAndResolveDisputeWithDelta() throws Exception {
        DisputeCreateRequestDto createDto = DisputeCreateRequestDto.builder()
                .tripId(trip.getId())
                .settlementRunId(run.getId())
                .reason("Driver was required to pay toll without FASTAG reimbursement")
                .evidenceRef("FASTAG-RECEIPT-999")
                .build();

        // 1. Raise Dispute
        String raiseJson = mockMvc.perform(post("/api/disputes")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andReturn().getResponse().getContentAsString();

        Number disputeId = com.jayway.jsonpath.JsonPath.read(raiseJson, "$.id");

        // 2. GET open disputes
        mockMvc.perform(get("/api/disputes/pending")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // 3. Resolve Dispute with +₹100 delta (Original: 250 -> Revised: 350)
        DisputeResolveRequestDto resolveDto = DisputeResolveRequestDto.builder()
                .approve(true)
                .revisedAmount(new BigDecimal("350.00"))
                .resolutionComments("Approved Fastag toll receipt")
                .build();

        mockMvc.perform(post("/api/disputes/" + disputeId + "/resolve")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispute.status", is("APPROVED")))
                .andExpect(jsonPath("$.adjustment.deltaAmount", is(100.00)))
                .andExpect(jsonPath("$.payoutInstruction.amount", is(100.00)))
                .andExpect(jsonPath("$.payoutInstruction.status", is("PENDING")));
    }
}

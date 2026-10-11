package com.moveinsync.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.SettlementCalculateRequestDto;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the Settlement REST API controller.
 *
 * <p>Validates HTTP endpoints, RBAC permissions, request validation,
 * Tier-1 idempotency headers, payload tamper detection, and approval transitions.
 *
 * @author MoveInSync Core Settlement Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SettlementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        UserDetails adminUser = userDetailsService.loadUserByUsername("admin");
        adminToken = "Bearer " + jwtService.generateToken(adminUser);
    }

    @Test
    @DisplayName("POST /api/settlements/calculate - Preview mode returns 200 OK without DB id")
    void shouldReturnPreviewCalculationSuccessfully() throws Exception {
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true // preview
        );

        mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", nullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalNet", is(64775.00)))
                .andExpect(jsonPath("$.tripCount", is(230)))
                .andExpect(jsonPath("$.driverCount", is(2)))
                .andExpect(jsonPath("$.driverPayslips", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/settlements/calculate - Tier-1 Idempotency replaying exact key returns cached response")
    void shouldHandleTier1IdempotencyReplay() throws Exception {
        String idemKey = "REST-KEY-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false // persistent
        );

        // First call
        String initialResponse = mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("CALCULATED")))
                .andReturn().getResponse().getContentAsString();

        // Second call with same key and body -> Returns cached identical run
        mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json(initialResponse));
    }

    @Test
    @DisplayName("POST /api/settlements/calculate - Tier-1 Idempotency tampering triggers 409 Conflict")
    void shouldTrigger409ConflictOnIdempotencyTampering() throws Exception {
        String idemKey = "TAMPER-KEY-" + UUID.randomUUID();
        SettlementCalculateRequestDto original = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false
        );

        SettlementCalculateRequestDto tampered = new SettlementCalculateRequestDto(
                2L, 3L, // changed vendor & BU
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false
        );

        // Register original
        mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(original)))
                .andExpect(status().isOk());

        // Replay same key with modified body -> 409 Conflict
        mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tampered)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Payload checksum mismatch")));
    }

    @Test
    @DisplayName("GET & POST approve - Full lifecycle from calculation to approval")
    void shouldCompleteFullSettlementLifecycle() throws Exception {
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false
        );

        String json = mockMvc.perform(post("/api/settlements/calculate")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number runId = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        // GET /{id}
        mockMvc.perform(get("/api/settlements/" + runId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(runId)))
                .andExpect(jsonPath("$.status", is("CALCULATED")))
                .andExpect(jsonPath("$.driverPayslips", hasSize(2)));

        // POST /{id}/approve
        mockMvc.perform(post("/api/settlements/" + runId + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(runId)))
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @DisplayName("POST /api/settlements/calculate - Unauthenticated call returns 401 Unauthorized")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true
        );

        mockMvc.perform(post("/api/settlements/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}

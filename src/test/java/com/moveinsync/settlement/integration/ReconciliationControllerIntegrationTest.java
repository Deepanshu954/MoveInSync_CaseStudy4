package com.moveinsync.settlement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.ReconcileRequestDto;
import com.moveinsync.settlement.dto.VendorInvoiceSubmitRequestDto;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorInvoiceRepository;
import com.moveinsync.settlement.repository.VendorRepository;
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
public class ReconciliationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    private String adminToken;
    private Vendor vendor;
    private SettlementRun run;

    @BeforeEach
    void setUp() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        adminToken = "Bearer " + jwtService.generateToken(admin);

        vendor = vendorRepository.findById(1L).orElseThrow();
        run = settlementRunRepository.findById(1L).orElseGet(() ->
                settlementRunRepository.save(SettlementRun.builder()
                        .vendor(vendor)
                        .periodStart(LocalDate.of(2026, 10, 1))
                        .periodEnd(LocalDate.of(2026, 10, 31))
                        .idempotencyKey("RECON-CTRL-" + UUID.randomUUID())
                        .totalGross(new BigDecimal("70000.00"))
                        .totalNet(new BigDecimal("64775.00"))
                        .tripCount(230)
                        .driverCount(2)
                        .status(SettlementRun.RunStatus.FINALIZED)
                        .build())
        );
    }

    @Test
    @DisplayName("POST /api/reconciliations/invoices & /compare - End-to-end reconciliation flow")
    void shouldSubmitAndReconcileInvoiceSuccessfully() throws Exception {
        String invoiceRef = "INV-REST-" + UUID.randomUUID().toString().substring(0, 8);
        VendorInvoiceSubmitRequestDto submitDto = VendorInvoiceSubmitRequestDto.builder()
                .vendorId(vendor.getId())
                .businessUnitId(1L)
                .invoiceRef(invoiceRef)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 31))
                .amount(new BigDecimal("64800.00"))
                .currency("INR")
                .build();

        // 1. Submit Invoice
        String submitJson = mockMvc.perform(post("/api/reconciliations/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Number invoiceId = com.jayway.jsonpath.JsonPath.read(submitJson, "$.id");

        // 2. GET invoice by ID
        mockMvc.perform(get("/api/reconciliations/invoices/" + invoiceId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceRef", is(invoiceRef)));

        // 3. Compare with settlement run (variance = 25.00 <= tolerance 50.00 -> MATCHED)
        ReconcileRequestDto compareDto = ReconcileRequestDto.builder()
                .invoiceId(invoiceId.longValue())
                .settlementRunId(run.getId())
                .tolerance(new BigDecimal("50.00"))
                .build();

        mockMvc.perform(post("/api/reconciliations/compare")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compareDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MATCHED")))
                .andExpect(jsonPath("$.difference", is(25.00)));
    }
}

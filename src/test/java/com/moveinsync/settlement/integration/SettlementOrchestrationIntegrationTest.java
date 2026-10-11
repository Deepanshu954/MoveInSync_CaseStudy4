package com.moveinsync.settlement.integration;

import com.moveinsync.settlement.dto.DriverPayslipResponseDto;
import com.moveinsync.settlement.dto.SettlementCalculateRequestDto;
import com.moveinsync.settlement.dto.SettlementRunResponseDto;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.repository.DriverPayslipRepository;
import com.moveinsync.settlement.repository.SettlementLineRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.service.SettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementOrchestrationIntegrationTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private DriverPayslipRepository driverPayslipRepository;

    @Autowired
    private SettlementLineRepository settlementLineRepository;

    @Test
    @DisplayName("Preview mode calculates correct figures without persisting records to PostgreSQL")
    void shouldPreviewSettlementWithoutPersistence() {
        long runCountBefore = settlementRunRepository.count();
        long payslipCountBefore = driverPayslipRepository.count();
        long lineCountBefore = settlementLineRepository.count();

        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true // PREVIEW ONLY
        );

        SettlementRunResponseDto preview = settlementService.calculateOrPreviewSettlement(request, null, "TEST_ADMIN");

        assertThat(preview).isNotNull();
        assertThat(preview.getId()).isNull(); // No ID assigned
        assertThat(preview.getStatus()).isEqualTo(SettlementRun.RunStatus.PENDING);
        assertThat(preview.getTripCount()).isEqualTo(230); // 180 trips (Raj) + 50 trips (Priya)
        assertThat(preview.getDriverCount()).isEqualTo(2);
        assertThat(preview.getDriverPayslips()).hasSize(2);

        // Verify zero database writes occurred
        assertThat(settlementRunRepository.count()).isEqualTo(runCountBefore);
        assertThat(driverPayslipRepository.count()).isEqualTo(payslipCountBefore);
        assertThat(settlementLineRepository.count()).isEqualTo(lineCountBefore);
    }

    @Test
    @DisplayName("Persistent run saves run, payslips, lines, and maintains exact mathematical identity")
    void shouldCalculateAndPersistWithZeroDrift() {
        String key = "INTEG-KEY-" + UUID.randomUUID();
        SettlementCalculateRequestDto request = new SettlementCalculateRequestDto(
                1L, 1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false // PERSIST
        );

        SettlementRunResponseDto run = settlementService.calculateOrPreviewSettlement(request, key, "TEST_ADMIN");

        assertThat(run.getId()).isNotNull();
        assertThat(run.getStatus()).isEqualTo(SettlementRun.RunStatus.CALCULATED);
        assertThat(run.getDriverPayslips()).isNotEmpty();

        // Mathematical identity validation: Sum of payslips == Total Net
        BigDecimal sumPayslipsNet = run.getDriverPayslips().stream()
                .map(DriverPayslipResponseDto::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(run.getTotalNet()).isEqualByComparingTo(sumPayslipsNet);

        // Mathematical identity validation: Sum of itemized lines == Driver Net
        for (DriverPayslipResponseDto p : run.getDriverPayslips()) {
            BigDecimal sumLines = p.getLines().stream()
                    .map(l -> l.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(p.getNetPay()).isEqualByComparingTo(sumLines);
        }

        // Tier-2 Domain Scope Protection: Running again for same scope returns existing run
        SettlementRunResponseDto duplicateRun = settlementService.calculateOrPreviewSettlement(request, "NEW-KEY", "TEST_ADMIN");
        assertThat(duplicateRun.getId()).isEqualTo(run.getId());
        assertThat(duplicateRun.getTotalNet()).isEqualByComparingTo(run.getTotalNet());

        // State Machine Transition: Approve calculated run
        SettlementRunResponseDto approved = settlementService.approveSettlementRun(run.getId(), "FINANCE_DIRECTOR");
        assertThat(approved.getStatus()).isEqualTo(SettlementRun.RunStatus.APPROVED);
    }
}

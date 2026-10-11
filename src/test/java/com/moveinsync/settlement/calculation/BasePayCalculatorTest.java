package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.Contract;
import com.moveinsync.settlement.entity.ContractRate;
import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasePayCalculatorTest {

    private BasePayCalculator calculator;
    private Contract contract;
    private Driver driver;

    @BeforeEach
    void setUp() {
        calculator = new BasePayCalculator();
        contract = new Contract();
        contract.setId(100L);
        driver = new Driver();
        driver.setId(200L);
    }

    @Test
    @DisplayName("Should resolve PER_TRIP and PER_DUTY contract rates cleanly")
    void shouldResolvePerTripAndPerDutyRates() {
        ContractRate rateTrip = new ContractRate();
        rateTrip.setId(1L);
        rateTrip.setContract(contract);
        rateTrip.setComponentType(ContractRate.ComponentType.PER_TRIP);
        rateTrip.setAmount(new BigDecimal("250.00"));
        rateTrip.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));
        rateTrip.setVersion(1);

        ContractRate rateDuty = new ContractRate();
        rateDuty.setId(2L);
        rateDuty.setContract(contract);
        rateDuty.setComponentType(ContractRate.ComponentType.PER_DUTY);
        rateDuty.setAmount(new BigDecimal("1200.00"));
        rateDuty.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));
        rateDuty.setVersion(1);

        Trip trip1 = new Trip();
        trip1.setId(10L);
        trip1.setExternalRef("TRIP-001");
        trip1.setContract(contract);
        trip1.setDriver(driver);
        trip1.setDutyType(Trip.DutyType.TRIP);
        trip1.setCompletedAt(Instant.parse("2026-10-05T10:00:00Z"));

        Trip trip2 = new Trip();
        trip2.setId(20L);
        trip2.setExternalRef("TRIP-002");
        trip2.setContract(contract);
        trip2.setDriver(driver);
        trip2.setDutyType(Trip.DutyType.DUTY);
        trip2.setCompletedAt(Instant.parse("2026-10-05T18:00:00Z"));

        List<CalculatedLineItem> lines = calculator.calculate(List.of(trip1, trip2), List.of(rateTrip, rateDuty));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("250.00");
        assertThat(lines.get(0).getLineType()).isEqualTo(SettlementLine.LineType.BASE_PAY);
        assertThat(lines.get(1).getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Should enforce mid-month rate transition on exact half-open boundaries")
    void shouldHandleMidMonthRateAmendment() {
        // Oct 01 - Oct 15 rate: 250.00
        ContractRate rateV1 = new ContractRate();
        rateV1.setId(1L);
        rateV1.setContract(contract);
        rateV1.setComponentType(ContractRate.ComponentType.PER_TRIP);
        rateV1.setAmount(new BigDecimal("250.00"));
        rateV1.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));
        rateV1.setEffectiveTo(Instant.parse("2026-10-15T00:00:00Z"));
        rateV1.setVersion(1);

        // Oct 15 onwards rate amendment: 280.00
        ContractRate rateV2 = new ContractRate();
        rateV2.setId(2L);
        rateV2.setContract(contract);
        rateV2.setComponentType(ContractRate.ComponentType.PER_TRIP);
        rateV2.setAmount(new BigDecimal("280.00"));
        rateV2.setEffectiveFrom(Instant.parse("2026-10-15T00:00:00Z"));
        rateV2.setEffectiveTo(null);
        rateV2.setVersion(2);

        // Trip 1 second before amendment
        Trip beforeTrip = new Trip();
        beforeTrip.setId(1L);
        beforeTrip.setExternalRef("TRIP-BEFORE");
        beforeTrip.setContract(contract);
        beforeTrip.setDriver(driver);
        beforeTrip.setDutyType(Trip.DutyType.TRIP);
        beforeTrip.setCompletedAt(Instant.parse("2026-10-14T23:59:59Z"));

        // Trip exactly on amendment timestamp
        Trip onBoundaryTrip = new Trip();
        onBoundaryTrip.setId(2L);
        onBoundaryTrip.setExternalRef("TRIP-ON");
        onBoundaryTrip.setContract(contract);
        onBoundaryTrip.setDriver(driver);
        onBoundaryTrip.setDutyType(Trip.DutyType.TRIP);
        onBoundaryTrip.setCompletedAt(Instant.parse("2026-10-15T00:00:00Z"));

        List<CalculatedLineItem> lines = calculator.calculate(List.of(beforeTrip, onBoundaryTrip), List.of(rateV1, rateV2));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("250.00");
        assertThat(lines.get(0).getRateVersion()).isEqualTo(1);
        assertThat(lines.get(1).getAmount()).isEqualByComparingTo("280.00");
        assertThat(lines.get(1).getRateVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw PolicyViolationException when no rate matches trip timestamp")
    void shouldThrowExceptionWhenNoRateFound() {
        ContractRate rate = new ContractRate();
        rate.setId(1L);
        rate.setContract(contract);
        rate.setComponentType(ContractRate.ComponentType.PER_TRIP);
        rate.setAmount(new BigDecimal("250.00"));
        rate.setEffectiveFrom(Instant.parse("2026-10-15T00:00:00Z"));

        Trip earlyTrip = new Trip();
        earlyTrip.setId(1L);
        earlyTrip.setExternalRef("TRIP-EARLY");
        earlyTrip.setContract(contract);
        earlyTrip.setDriver(driver);
        earlyTrip.setDutyType(Trip.DutyType.TRIP);
        earlyTrip.setCompletedAt(Instant.parse("2026-10-01T12:00:00Z"));

        assertThatThrownBy(() -> calculator.calculate(List.of(earlyTrip), List.of(rate)))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("No effective contract rate found");
    }
}

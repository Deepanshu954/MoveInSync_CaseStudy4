package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.IncentivePolicy;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncentiveCalculatorTest {

    private IncentiveCalculator calculator;
    private Driver driver;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        calculator = new IncentiveCalculator();
        driver = new Driver();
        driver.setId(10L);
        start = LocalDate.of(2026, 10, 1);
        end = LocalDate.of(2026, 10, 31);
    }

    @Test
    @DisplayName("Should qualify for ON_TIME_BONUS when punctuality >= 90.00%")
    void shouldQualifyOnTimeBonus() {
        IncentivePolicy policy = new IncentivePolicy();
        policy.setId(1L);
        policy.setType(IncentivePolicy.IncentiveType.ON_TIME_BONUS);
        policy.setAmount(new BigDecimal("1500.00"));
        policy.setThresholdPercent(new BigDecimal("90.00"));
        policy.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));
        policy.setVersion(1);

        // 9 on-time trips out of 10 = 90.00%
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Trip t = new Trip();
            t.setId((long) i);
            t.setDriver(driver);
            t.setIsOnTime(i < 9); // 9 on-time, 1 delayed
            t.setCompletedAt(Instant.parse("2026-10-05T10:00:00Z"));
            trips.add(t);
        }

        List<CalculatedLineItem> lines = calculator.calculate(trips, List.of(policy), start, end);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineType()).isEqualTo(SettlementLine.LineType.INCENTIVE);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Should disqualify ON_TIME_BONUS when punctuality is 89.99%")
    void shouldDisqualifyWhenBelowThreshold() {
        IncentivePolicy policy = new IncentivePolicy();
        policy.setId(1L);
        policy.setType(IncentivePolicy.IncentiveType.ON_TIME_BONUS);
        policy.setAmount(new BigDecimal("1500.00"));
        policy.setThresholdPercent(new BigDecimal("90.00"));
        policy.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));

        // 8 on-time trips out of 10 = 80.00% < 90.00%
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Trip t = new Trip();
            t.setId((long) i);
            t.setDriver(driver);
            t.setIsOnTime(i < 8);
            t.setCompletedAt(Instant.parse("2026-10-05T10:00:00Z"));
            trips.add(t);
        }

        List<CalculatedLineItem> lines = calculator.calculate(trips, List.of(policy), start, end);
        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("Should qualify WORKED_DAYS_BONUS based on distinct calendar days in IST")
    void shouldQualifyWorkedDaysBonus() {
        IncentivePolicy policy = new IncentivePolicy();
        policy.setId(2L);
        policy.setType(IncentivePolicy.IncentiveType.WORKED_DAYS_BONUS);
        policy.setAmount(new BigDecimal("2000.00"));
        policy.setThresholdValue(5); // 5 distinct days
        policy.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));

        List<Trip> trips = new ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            Trip t = new Trip();
            t.setId((long) day);
            t.setDriver(driver);
            t.setIsOnTime(true);
            t.setCompletedAt(Instant.parse(String.format("2026-10-%02dT10:00:00Z", day)));
            trips.add(t);
        }

        List<CalculatedLineItem> lines = calculator.calculate(trips, List.of(policy), start, end);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("2000.00");
    }
}

package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.PenaltyPolicy;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.entity.TripViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PenaltyCalculatorTest {

    private PenaltyCalculator calculator;
    private Driver driver;
    private Trip trip;

    @BeforeEach
    void setUp() {
        calculator = new PenaltyCalculator();
        driver = new Driver();
        driver.setId(50L);
        trip = new Trip();
        trip.setId(100L);
        trip.setDriver(driver);
    }

    @Test
    @DisplayName("Should enforce daily frequency cap and skip dismissed infractions")
    void shouldEnforceFrequencyCapAndSkipDismissed() {
        PenaltyPolicy policy = new PenaltyPolicy();
        policy.setId(1L);
        policy.setViolationType(PenaltyPolicy.ViolationType.OVER_SPEED);
        policy.setAmount(new BigDecimal("500.00"));
        policy.setFrequencyWindow(PenaltyPolicy.FrequencyWindow.DAILY);
        policy.setMaxOccurrences(2); // Max 2 per day
        policy.setVersion(1);

        // 3 active speeding violations on the same day + 1 dismissed violation
        TripViolation v1 = createViolation(1L, PenaltyPolicy.ViolationType.OVER_SPEED, "2026-10-10T08:00:00Z", TripViolation.ViolationStatus.ACTIVE);
        TripViolation v2 = createViolation(2L, PenaltyPolicy.ViolationType.OVER_SPEED, "2026-10-10T11:00:00Z", TripViolation.ViolationStatus.ACTIVE);
        TripViolation v3 = createViolation(3L, PenaltyPolicy.ViolationType.OVER_SPEED, "2026-10-10T14:00:00Z", TripViolation.ViolationStatus.ACTIVE); // should be capped
        TripViolation vDismissed = createViolation(4L, PenaltyPolicy.ViolationType.OVER_SPEED, "2026-10-10T16:00:00Z", TripViolation.ViolationStatus.DISMISSED);

        List<CalculatedLineItem> lines = calculator.calculate(
                List.of(v1, v2, v3, vDismissed),
                List.of(policy)
        );

        // Only first 2 active violations incur penalty
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("-500.00");
        assertThat(lines.get(0).getLineType()).isEqualTo(SettlementLine.LineType.PENALTY);
        assertThat(lines.get(1).getAmount()).isEqualByComparingTo("-500.00");
    }

    private TripViolation createViolation(Long id, PenaltyPolicy.ViolationType type, String time, TripViolation.ViolationStatus status) {
        TripViolation v = new TripViolation();
        v.setId(id);
        v.setTrip(trip);
        v.setViolationType(type);
        v.setEventAt(Instant.parse(time));
        v.setStatus(status);
        return v;
    }
}

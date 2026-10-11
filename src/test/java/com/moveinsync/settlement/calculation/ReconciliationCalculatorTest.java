package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.ReconciliationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationCalculatorTest {

    private ReconciliationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ReconciliationCalculator();
    }

    @Test
    @DisplayName("Should MATCH when calculated equals invoice amount exactly (difference = 0)")
    void testExactMatch() {
        BigDecimal amount = new BigDecimal("64775.00");
        ReconciliationCalculator.ReconciliationResult result = calculator.calculate(
                amount, amount, new BigDecimal("50.00"), null
        );

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ReconciliationRecord.ReconciliationStatus.MATCHED);
        assertThat(result.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getEffectiveTolerance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should MATCH when difference is within absolute tolerance (20 <= 50)")
    void testWithinAbsoluteTolerance() {
        BigDecimal calc = new BigDecimal("64775.00");
        BigDecimal invoiced = new BigDecimal("64795.00"); // diff = 20.00

        ReconciliationCalculator.ReconciliationResult result = calculator.calculate(
                calc, invoiced, new BigDecimal("50.00"), null
        );

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ReconciliationRecord.ReconciliationStatus.MATCHED);
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Should flag DISCREPANCY when difference exceeds absolute tolerance (100 > 50)")
    void testExceedsAbsoluteTolerance() {
        BigDecimal calc = new BigDecimal("64775.00");
        BigDecimal invoiced = new BigDecimal("64875.00"); // diff = 100.00

        ReconciliationCalculator.ReconciliationResult result = calculator.calculate(
                calc, invoiced, new BigDecimal("50.00"), null
        );

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ReconciliationRecord.ReconciliationStatus.DISCREPANCY);
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate effective tolerance using percentage when provided (0.5% of 100,000 = 500)")
    void testPercentageTolerance() {
        BigDecimal calc = new BigDecimal("100000.00");
        BigDecimal invoiced = new BigDecimal("100400.00"); // diff = 400.00

        // 0.5% tolerance on 100,000 is 500.00. 400.00 <= 500.00 -> MATCHED
        ReconciliationCalculator.ReconciliationResult result = calculator.calculate(
                calc, invoiced, null, new BigDecimal("0.5")
        );

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getEffectiveTolerance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("400.00"));
    }
}

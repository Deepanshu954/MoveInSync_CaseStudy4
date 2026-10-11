package com.moveinsync.settlement.dispute.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisputeDeltaCalculatorTest {

    private DisputeDeltaCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DisputeDeltaCalculator();
    }

    @Test
    @DisplayName("Should calculate positive delta when revised fare exceeds original (250 -> 350 = +100)")
    void testPositiveDelta() {
        BigDecimal original = new BigDecimal("250.00");
        BigDecimal revised = new BigDecimal("350.00");

        DisputeDeltaCalculator.DisputeDeltaResult result = calculator.calculateDelta(original, revised);

        assertThat(result.getOriginalAmount()).isEqualByComparingTo(original);
        assertThat(result.getRevisedAmount()).isEqualByComparingTo(revised);
        assertThat(result.getDeltaAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.isPayable()).isTrue();
        assertThat(result.isNegativeAdjustment()).isFalse();
    }

    @Test
    @DisplayName("Should calculate zero delta when revised equals original")
    void testZeroDelta() {
        BigDecimal original = new BigDecimal("250.00");
        BigDecimal revised = new BigDecimal("250.00");

        DisputeDeltaCalculator.DisputeDeltaResult result = calculator.calculateDelta(original, revised);

        assertThat(result.getDeltaAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isPayable()).isFalse();
        assertThat(result.isNegativeAdjustment()).isFalse();
    }

    @Test
    @DisplayName("Should detect negative adjustment when revised is less than original (overpayment recovery)")
    void testNegativeDelta() {
        BigDecimal original = new BigDecimal("300.00");
        BigDecimal revised = new BigDecimal("200.00");

        DisputeDeltaCalculator.DisputeDeltaResult result = calculator.calculateDelta(original, revised);

        assertThat(result.getDeltaAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(result.isPayable()).isFalse();
        assertThat(result.isNegativeAdjustment()).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when revised amount is null or negative")
    void testInvalidRevisedAmountThrows() {
        assertThatThrownBy(() -> calculator.calculateDelta(new BigDecimal("200.00"), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> calculator.calculateDelta(new BigDecimal("200.00"), new BigDecimal("-50.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

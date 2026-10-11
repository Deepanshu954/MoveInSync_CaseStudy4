package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.TaxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    private TaxCalculator calculator;
    private Driver driver;
    private TaxConfig taxConfig;

    @BeforeEach
    void setUp() {
        calculator = new TaxCalculator();
        driver = new Driver();
        driver.setId(1L);

        taxConfig = new TaxConfig();
        taxConfig.setId(10L);
        taxConfig.setName("Section 194C Individual");
        taxConfig.setMonthlyThreshold(new BigDecimal("30000.00"));
        taxConfig.setRatePercent(new BigDecimal("1.00"));
        taxConfig.setVersion(1);
    }

    @Test
    @DisplayName("Earnings below or equal to threshold incur zero TDS")
    void shouldIncurZeroTdsWhenBelowOrEqualThreshold() {
        // Below
        Optional<CalculatedLineItem> below = calculator.calculate(driver, new BigDecimal("25000.00"), taxConfig);
        assertThat(below).isEmpty();

        // Exactly equal
        Optional<CalculatedLineItem> equal = calculator.calculate(driver, new BigDecimal("30000.00"), taxConfig);
        assertThat(equal).isEmpty();
    }

    @Test
    @DisplayName("Earnings above threshold incur exact 1% TDS on excess")
    void shouldCalculateTdsOnExcess() {
        // Earnings ₹50,000 -> Excess ₹20,000 * 1% = ₹200.00
        Optional<CalculatedLineItem> opt = calculator.calculate(driver, new BigDecimal("50000.00"), taxConfig);

        assertThat(opt).isPresent();
        CalculatedLineItem line = opt.get();
        assertThat(line.getAmount()).isEqualByComparingTo("-200.00");
        assertThat(line.getLineType()).isEqualTo(SettlementLine.LineType.TDS);
    }

    @Test
    @DisplayName("Calculates fractional paise TDS with HALF_UP rounding")
    void shouldRoundTdsToPaise() {
        // Earnings ₹30,123.45 -> Excess ₹123.45 * 1% = ₹1.2345 -> rounded to ₹1.23
        Optional<CalculatedLineItem> opt = calculator.calculate(driver, new BigDecimal("30123.45"), taxConfig);

        assertThat(opt).isPresent();
        assertThat(opt.get().getAmount()).isEqualByComparingTo("-1.23");
    }
}

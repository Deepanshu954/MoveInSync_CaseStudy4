package com.moveinsync.settlement.payout.calculator;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.DriverPayslip;
import com.moveinsync.settlement.entity.SettlementRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutCalculatorTest {

    private PayoutCalculator payoutCalculator;
    private SettlementRun run;
    private Driver driver1;
    private Driver driver2;

    @BeforeEach
    void setUp() {
        payoutCalculator = new PayoutCalculator();
        run = new SettlementRun();
        run.setId(10L);

        driver1 = new Driver();
        driver1.setId(100L);

        driver2 = new Driver();
        driver2.setId(200L);
    }

    @Test
    @DisplayName("Should pass when sum of payslips exactly matches run total net")
    void shouldPassWhenSumOfPayslipsMatchesRunTotalNet() {
        run.setTotalNet(new BigDecimal("15000.50"));

        DriverPayslip p1 = new DriverPayslip();
        p1.setDriver(driver1);
        p1.setNetPay(new BigDecimal("10000.25"));

        DriverPayslip p2 = new DriverPayslip();
        p2.setDriver(driver2);
        p2.setNetPay(new BigDecimal("5000.25"));

        assertThatCode(() -> payoutCalculator.verifyFinancialInvariant(run, List.of(p1, p2)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw PolicyViolationException when even 1 paisa discrepancy exists")
    void shouldThrowPolicyViolationWhenDiscrepancyExists() {
        run.setTotalNet(new BigDecimal("15000.50"));

        DriverPayslip p1 = new DriverPayslip();
        p1.setDriver(driver1);
        p1.setNetPay(new BigDecimal("10000.25"));

        DriverPayslip p2 = new DriverPayslip();
        p2.setDriver(driver2);
        p2.setNetPay(new BigDecimal("5000.24"));

        assertThatThrownBy(() -> payoutCalculator.verifyFinancialInvariant(run, List.of(p1, p2)))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Financial integrity check failed");
    }
}

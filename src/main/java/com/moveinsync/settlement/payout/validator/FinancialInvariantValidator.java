package com.moveinsync.settlement.payout.validator;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.DriverPayslip;
import com.moveinsync.settlement.entity.SettlementRun;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Validates financial totals before payout disbursement.
 * Checks that the sum of individual driver payslips equals the settlement run net amount.
 *
 * @author Deepanshu
 */
@Component
public class FinancialInvariantValidator {

    /**
     * Asserts that sum of individual payslip net pay strictly matches the run's stated net total.
     *
     * @param run the settlement run being finalized
     * @param payslips list of driver payslips generated for this run
     * @throws PolicyViolationException if amounts do not match exactly
     */
    @SuppressWarnings("null")
    public void validateZeroDrift(SettlementRun run, List<DriverPayslip> payslips) {
        BigDecimal sumOfPayslips = payslips.stream()
                .map(p -> p.getNetPay())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b))
                .setScale(2, RoundingMode.HALF_UP);

        if (run.getTotalNet().compareTo(sumOfPayslips) != 0) {
            throw new PolicyViolationException(String.format(
                    "Financial integrity check failed: Run total net (%s) does not match sum of payslips (%s)",
                    run.getTotalNet().toPlainString(), sumOfPayslips.toPlainString()
            ));
        }
    }
}

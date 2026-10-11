package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.DriverPayslip;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.payout.validator.FinancialInvariantValidator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates settlement payout totals match payslip sums before triggering disbursements.
 *
 * @author Deepanshu
 */
@Component
public class PayoutCalculator extends FinancialInvariantValidator {

    /**
     * Verifies that the mathematical sum of driver payslips matches the settlement run net amount.
     *
     * @param run      the settlement run under evaluation
     * @param payslips the list of driver payslips to sum
     * @throws PolicyViolationException if any drift or discrepancy is detected
     */
    public void verifyFinancialInvariant(SettlementRun run, List<DriverPayslip> payslips) {
        validateZeroDrift(run, payslips);
    }
}

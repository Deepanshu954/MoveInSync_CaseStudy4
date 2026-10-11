package com.moveinsync.settlement.payout.calculator;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.DriverPayslip;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.payout.validator.FinancialInvariantValidator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pure calculation and validation component for financial invariant verification.
 *
 * <p>Enforces zero mathematical drift between the settlement run total net pay
 * and the sum of individual driver payslips prior to payout disbursement.
 *
 * @author MoveInSync Core Settlement Team
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

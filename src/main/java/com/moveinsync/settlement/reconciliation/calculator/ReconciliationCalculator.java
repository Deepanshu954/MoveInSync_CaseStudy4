package com.moveinsync.settlement.reconciliation.calculator;

import com.moveinsync.settlement.entity.ReconciliationRecord;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculation engine for three-way vendor invoice reconciliation.
 *
 * <p>Enforces Single Responsibility Principle (SRP):
 * Evaluates variance between billed amounts and calculated settlement ledgers
 * against configurable absolute or percentage tolerances.
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
public class ReconciliationCalculator {

    @Getter
    @Builder
    public static class ReconciliationResult {
        private final BigDecimal calculatedAmount;
        private final BigDecimal invoiceAmount;
        private final BigDecimal difference;
        private final BigDecimal effectiveTolerance;
        private final ReconciliationRecord.ReconciliationStatus status;
        private final String notes;
        private final boolean matched;
    }

    /**
     * Evaluates variance between calculated ledger and vendor invoice against configured tolerance.
     *
     * @param calculatedAmount engine-calculated total (Net or Gross)
     * @param invoiceAmount vendor-claimed billed amount
     * @param absoluteTolerance fixed currency variance threshold (default ₹50.00)
     * @param percentageTolerance optional relative percentage variance threshold (e.g. 0.5% = 0.5)
     * @return structured reconciliation verdict with notes
     */
    public ReconciliationResult calculate(
            BigDecimal calculatedAmount,
            BigDecimal invoiceAmount,
            BigDecimal absoluteTolerance,
            BigDecimal percentageTolerance
    ) {
        BigDecimal calc = (calculatedAmount != null ? calculatedAmount : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal invoiced = (invoiceAmount != null ? invoiceAmount : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal difference = calc.subtract(invoiced).abs().setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectiveTolerance;
        if (percentageTolerance != null && percentageTolerance.compareTo(BigDecimal.ZERO) > 0) {
            effectiveTolerance = calc.multiply(percentageTolerance)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else if (absoluteTolerance != null && absoluteTolerance.compareTo(BigDecimal.ZERO) >= 0) {
            effectiveTolerance = absoluteTolerance.setScale(2, RoundingMode.HALF_UP);
        } else {
            effectiveTolerance = new BigDecimal("50.00");
        }

        boolean matched = difference.compareTo(effectiveTolerance) <= 0;
        ReconciliationRecord.ReconciliationStatus status = matched
                ? ReconciliationRecord.ReconciliationStatus.MATCHED
                : ReconciliationRecord.ReconciliationStatus.DISCREPANCY;

        String notes = matched
                ? String.format("Invoice matched within acceptable tolerance. Variance: ₹%s (<= ₹%s).",
                        difference.toPlainString(), effectiveTolerance.toPlainString())
                : String.format("Invoice variance exceeds threshold. Calculated: ₹%s, Invoiced: ₹%s, Difference: ₹%s (> ₹%s).",
                        calc.toPlainString(), invoiced.toPlainString(), difference.toPlainString(), effectiveTolerance.toPlainString());

        return ReconciliationResult.builder()
                .calculatedAmount(calc)
                .invoiceAmount(invoiced)
                .difference(difference)
                .effectiveTolerance(effectiveTolerance)
                .status(status)
                .notes(notes)
                .matched(matched)
                .build();
    }
}

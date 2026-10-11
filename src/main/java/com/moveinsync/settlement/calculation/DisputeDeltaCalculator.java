package com.moveinsync.settlement.calculation;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates payment deltas for resolved disputes (revised amount minus original amount)
 * without altering existing historical run records.
 *
 * @author Deepanshu
 */
@Component
public class DisputeDeltaCalculator {

    @Getter
    @Builder
    public static class DisputeDeltaResult {
        private final BigDecimal originalAmount;
        private final BigDecimal revisedAmount;
        private final BigDecimal deltaAmount;
        private final boolean payable;
        private final boolean negativeAdjustment;
    }

    /**
     * Calculates closed-loop delta adjustment between revised fare and historical baseline.
     *
     * @param originalAmount original trip baseline amount recorded in settlement_line
     * @param revisedAmount approved revised fare claimed by driver/vendor
     * @return calculated delta result
     */
    public DisputeDeltaResult calculateDelta(BigDecimal originalAmount, BigDecimal revisedAmount) {
        if (revisedAmount == null || revisedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Revised amount must be specified and non-negative when resolving a dispute");
        }

        BigDecimal original = (originalAmount != null ? originalAmount : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal revised = revisedAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = revised.subtract(original).setScale(2, RoundingMode.HALF_UP);

        boolean payable = delta.compareTo(BigDecimal.ZERO) > 0;
        boolean negative = delta.compareTo(BigDecimal.ZERO) < 0;

        return DisputeDeltaResult.builder()
                .originalAmount(original)
                .revisedAmount(revised)
                .deltaAmount(delta)
                .payable(payable)
                .negativeAdjustment(negative)
                .build();
    }
}

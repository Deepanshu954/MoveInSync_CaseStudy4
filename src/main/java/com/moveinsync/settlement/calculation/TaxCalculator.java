package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.TaxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pure calculation engine evaluating statutory Indian Income Tax Section 194C TDS withholding.
 *
 * <p>Statutory Rules:
 * <ol>
 *   <li><b>Cumulative Threshold Exemption:</b>
 *       TDS applies strictly to the earnings exceeding the monthly exemption limit (e.g. ₹30,000 threshold).
 *       <pre>
 *         TaxableEarnings = GrossBasePay + TotalIncentives
 *         TaxableExcess = max(0, TaxableEarnings - MonthlyThreshold)
 *       </pre>
 *   </li>
 *   <li><b>Statutory Withholding:</b>
 *       If {@code TaxableExcess > 0}, TDS is deducted as:
 *       <pre>
 *         TDS = (TaxableExcess * RatePercent) / 100  [rounded HALF_UP to 2 decimal places]
 *       </pre>
 *   </li>
 *   <li><b>Signed Arithmetic:</b> Withholding is signed negative (e.g. {@code -₹200.00}).</li>
 * </ol>
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
public class TaxCalculator {

    private static final Logger log = LoggerFactory.getLogger(TaxCalculator.class);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Calculates Section 194C TDS withholding line item for a driver's monthly taxable earnings.
     *
     * @param driver          driver recipient
     * @param taxableEarnings cumulative gross earnings (base trip fares + earned incentives)
     * @param config          active statutory tax rate and threshold configuration
     * @return optional debit {@link CalculatedLineItem} if taxable excess exceeds zero
     */
    public Optional<CalculatedLineItem> calculate(Driver driver, BigDecimal taxableEarnings, TaxConfig config) {
        if (driver == null || taxableEarnings == null || config == null) {
            return Optional.empty();
        }

        BigDecimal threshold = config.getMonthlyThreshold() != null
                ? config.getMonthlyThreshold()
                : BigDecimal.ZERO;

        BigDecimal excess = taxableEarnings.subtract(threshold);

        // If earnings do not exceed the statutory threshold, zero TDS is incurred
        if (excess.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Driver {} taxable earnings (₹{}) within threshold (₹{}). Zero TDS.",
                    driver.getId(), taxableEarnings, threshold);
            return Optional.empty();
        }

        BigDecimal ratePercent = config.getRatePercent() != null ? config.getRatePercent() : BigDecimal.ZERO;
        BigDecimal tdsAmount = excess.multiply(ratePercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        if (tdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal signedTds = tdsAmount.negate().setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("driverId", driver.getId());
        details.put("taxConfigId", config.getId());
        details.put("taxRegime", config.getName());
        details.put("taxableEarnings", taxableEarnings.setScale(2, RoundingMode.HALF_UP).toPlainString());
        details.put("monthlyThreshold", threshold.setScale(2, RoundingMode.HALF_UP).toPlainString());
        details.put("taxableExcess", excess.setScale(2, RoundingMode.HALF_UP).toPlainString());
        details.put("ratePercent", ratePercent.toPlainString());
        details.put("tdsDeducted", tdsAmount.toPlainString());

        CalculatedLineItem line = new CalculatedLineItem(
                driver,
                SettlementLine.LineType.TDS,
                "TAX_CONFIG",
                config.getId(),
                String.format("TDS Deduction under Section 194C (%s%% on excess over ₹%s)",
                        ratePercent.toPlainString(), threshold.toPlainString()),
                signedTds,
                null,
                config.getVersion(),
                details
        );

        log.debug("Computed TDS for driver {}: {}", driver.getId(), line);
        return Optional.of(line);
    }
}

package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.TaxConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calculates TDS (Tax Deducted at Source) under Section 194C.
 * Applies withholding tax on taxable earnings exceeding the configured exemption threshold.
 * Deductions are stored as negative amounts.
 *
 * @author Deepanshu
 */
@Component
@Slf4j
public class TaxCalculator {

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
        BigDecimal tdsAmount;
        if (ratePercent.compareTo(BigDecimal.ONE) >= 0) {
            // Specified as whole percentage (e.g. 1.00 for 1%, 10.00 for 10%)
            tdsAmount = excess.multiply(ratePercent).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            // Specified as ratio fraction (e.g. 0.1000 for 10%, 0.0100 for 1%)
            tdsAmount = excess.multiply(ratePercent).setScale(2, RoundingMode.HALF_UP);
        }

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

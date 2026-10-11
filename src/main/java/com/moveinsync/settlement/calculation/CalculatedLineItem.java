package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a single calculated financial line item
 * before persistence to the database.
 *
 * <p>Financial Conventions:
 * <ul>
 *   <li><b>Credit items (earnings, bonuses):</b> Represented as positive {@link BigDecimal} values.</li>
 *   <li><b>Debit items (penalties, tax deductions):</b> Represented as negative signed {@link BigDecimal} values.</li>
 *   <li><b>Precision:</b> Standardized to 2 decimal places using {@link RoundingMode#HALF_UP}.</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
public class CalculatedLineItem {

    private final Driver driver;
    private final SettlementLine.LineType lineType;
    private final String sourceType;
    private final Long sourceId;
    private final String description;
    private final BigDecimal amount;
    private final Integer rateVersion;
    private final Integer policyVersion;
    private final Map<String, Object> calculationDetails;

    public CalculatedLineItem(
            Driver driver,
            SettlementLine.LineType lineType,
            String sourceType,
            Long sourceId,
            String description,
            BigDecimal amount,
            Integer rateVersion,
            Integer policyVersion,
            Map<String, Object> calculationDetails
    ) {
        this.driver = Objects.requireNonNull(driver, "Driver must not be null");
        this.lineType = Objects.requireNonNull(lineType, "LineType must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "SourceType must not be null");
        this.sourceId = sourceId;
        this.description = Objects.requireNonNull(description, "Description must not be null");
        this.amount = Objects.requireNonNull(amount, "Amount must not be null").setScale(2, RoundingMode.HALF_UP);
        this.rateVersion = rateVersion;
        this.policyVersion = policyVersion;
        this.calculationDetails = calculationDetails != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(calculationDetails))
                : Collections.emptyMap();
    }

    public Driver getDriver() {
        return driver;
    }

    public SettlementLine.LineType getLineType() {
        return lineType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getRateVersion() {
        return rateVersion;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public Map<String, Object> getCalculationDetails() {
        return calculationDetails;
    }

    @Override
    public String toString() {
        return "CalculatedLineItem{" +
                "driver=" + (driver != null ? driver.getId() : "null") +
                ", lineType=" + lineType +
                ", sourceType='" + sourceType + '\'' +
                ", sourceId=" + sourceId +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", rateVersion=" + rateVersion +
                ", policyVersion=" + policyVersion +
                '}';
    }
}

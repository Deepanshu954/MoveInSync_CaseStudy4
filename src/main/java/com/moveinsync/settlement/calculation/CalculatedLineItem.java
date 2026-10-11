package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.SettlementLine;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CalculatedLineItem {

    private Driver driver;
    private SettlementLine.LineType lineType;
    private String sourceType;
    private Long sourceId;
    private String description;
    private BigDecimal amount;
    private Integer rateVersion;
    private Integer policyVersion;

    @Builder.Default
    private Map<String, Object> calculationDetails = Collections.emptyMap();
}

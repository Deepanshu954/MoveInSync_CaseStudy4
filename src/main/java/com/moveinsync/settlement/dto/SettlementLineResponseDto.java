package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.SettlementLine;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO representing an atomic line item in a payslip ledger.
 *
 * @author Deepanshu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SettlementLineResponseDto {

    private Long id;
    private SettlementLine.LineType lineType;
    private String sourceType;
    private Long sourceId;
    private String description;
    private BigDecimal amount;
    private Integer rateVersion;
    private Integer policyVersion;
    private Map<String, Object> calculationDetails;
}

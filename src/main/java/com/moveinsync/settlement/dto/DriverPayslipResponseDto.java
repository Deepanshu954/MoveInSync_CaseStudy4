package com.moveinsync.settlement.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary and itemized breakdown of a single driver's payslip in a settlement run.
 *
 * @author Deepanshu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DriverPayslipResponseDto {

    private Long id;
    private Long driverId;
    private String driverName;
    private String externalRef;
    private BigDecimal grossPay;
    private BigDecimal totalIncentives;
    private BigDecimal totalPenalties;
    private BigDecimal totalTds;
    private BigDecimal netPay;
    private Integer tripCount;

    @Builder.Default
    private List<SettlementLineResponseDto> lines = new ArrayList<>();
}

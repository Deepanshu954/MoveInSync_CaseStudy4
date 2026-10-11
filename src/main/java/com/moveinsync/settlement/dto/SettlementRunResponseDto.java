package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.SettlementRun;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level response DTO representing a complete settlement run summary and driver payslips.
 *
 * @author Deepanshu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SettlementRunResponseDto {

    private Long id;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private Long businessUnitId;
    private String businessUnitCode;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private SettlementRun.RunType runType;
    private SettlementRun.RunStatus status;
    private String idempotencyKey;
    private BigDecimal totalGross;
    private BigDecimal totalIncentives;
    private BigDecimal totalPenalties;
    private BigDecimal totalTds;
    private BigDecimal totalNet;
    private Integer tripCount;
    private Integer driverCount;
    private Instant createdAt;

    @Builder.Default
    private List<DriverPayslipResponseDto> driverPayslips = new ArrayList<>();
}



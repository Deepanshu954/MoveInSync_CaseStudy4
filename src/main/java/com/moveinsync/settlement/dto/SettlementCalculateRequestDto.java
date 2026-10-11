package com.moveinsync.settlement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

/**
 * Request payload for initiating or previewing a settlement calculation run.
 *
 * @author Deepanshu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SettlementCalculateRequestDto {

    @NotNull(message = "Vendor ID is mandatory")
    private Long vendorId;

    private Long businessUnitId;

    @NotNull(message = "Period start date is mandatory")
    private LocalDate periodStart;

    @NotNull(message = "Period end date is mandatory")
    private LocalDate periodEnd;

    @Builder.Default
    private boolean previewOnly = false;
}

package com.moveinsync.settlement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResolveRequestDto {
    private Long disputeId;
    @NotNull
    private Boolean approve;
    private BigDecimal revisedAmount;
    private String resolutionComments;
}

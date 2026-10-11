package com.moveinsync.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeCreateRequestDto {
    @NotNull
    private Long tripId;
    private Long settlementRunId;
    @NotBlank
    private String reason;
    private String evidenceRef;
}

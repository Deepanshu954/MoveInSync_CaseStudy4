package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.Dispute;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResponseDto {
    private Long id;
    private Long tripId;
    private String tripExternalRef;
    private Long driverId;
    private String driverName;
    private Long settlementRunId;
    private String raisedBy;
    private String reason;
    private String evidenceRef;
    private Dispute.DisputeStatus status;
    private String resolution;
    private String approvedBy;
    private Instant resolvedAt;
    private Instant createdAt;

    public static DisputeResponseDto fromEntity(Dispute d) {
        if (d == null) return null;
        return DisputeResponseDto.builder()
                .id(d.getId())
                .tripId(d.getTrip().getId())
                .tripExternalRef(d.getTrip().getExternalRef())
                .driverId(d.getTrip().getDriver().getId())
                .driverName(d.getTrip().getDriver().getName())
                .settlementRunId(d.getSettlementRun() != null ? d.getSettlementRun().getId() : null)
                .raisedBy(d.getRaisedBy())
                .reason(d.getReason())
                .evidenceRef(d.getEvidenceRef())
                .status(d.getStatus())
                .resolution(d.getResolution())
                .approvedBy(d.getApprovedBy())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}

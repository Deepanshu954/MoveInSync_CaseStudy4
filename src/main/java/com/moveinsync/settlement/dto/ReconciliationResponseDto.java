package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.ReconciliationRecord;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResponseDto {
    private Long id;
    private Long invoiceId;
    private String invoiceRef;
    private Long settlementRunId;
    private BigDecimal calculatedAmount;
    private BigDecimal invoiceAmount;
    private BigDecimal difference;
    private BigDecimal tolerance;
    private ReconciliationRecord.ReconciliationStatus status;
    private String accountingBasis;
    private String notes;
    private String resolvedBy;
    private Instant createdAt;

    public static ReconciliationResponseDto fromEntity(ReconciliationRecord r) {
        if (r == null) return null;
        return ReconciliationResponseDto.builder()
                .id(r.getId())
                .invoiceId(r.getInvoice().getId())
                .invoiceRef(r.getInvoice().getInvoiceRef())
                .settlementRunId(r.getSettlementRun() != null ? r.getSettlementRun().getId() : null)
                .calculatedAmount(r.getCalculatedAmount())
                .invoiceAmount(r.getInvoiceAmount())
                .difference(r.getDifference())
                .tolerance(r.getTolerance())
                .status(r.getStatus())
                .accountingBasis(r.getAccountingBasis())
                .notes(r.getNotes())
                .resolvedBy(r.getResolvedBy())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

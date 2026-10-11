package com.moveinsync.settlement.service;

import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.common.exception.ResourceNotFoundException;
import com.moveinsync.settlement.dto.BankingWebhookRequestDto;
import com.moveinsync.settlement.dto.PayoutInstructionResponseDto;
import com.moveinsync.settlement.entity.PayoutInstruction;
import com.moveinsync.settlement.repository.PayoutInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutInstructionRepository payoutInstructionRepository;

    @Transactional(readOnly = true)
    @ExecutionTime
    public PayoutInstructionResponseDto getPayoutById(Long id) {
        PayoutInstruction payout = payoutInstructionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutInstruction not found with id: " + id));
        return toDto(payout);
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<PayoutInstructionResponseDto> getPayoutsByRunId(Long runId) {
        return payoutInstructionRepository.findBySettlementRunId(runId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<PayoutInstructionResponseDto> getPayoutsByDriverId(Long driverId) {
        return payoutInstructionRepository.findByDriverId(driverId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Processes asynchronous webhook callbacks from banking partners (RazorpayX, ICICI, IMPS).
     */
    @Transactional
    @Audited(entityType = "PAYOUT_INSTRUCTION", action = "WEBHOOK_UPDATE")
    @ExecutionTime
    public PayoutInstructionResponseDto handleBankingWebhook(BankingWebhookRequestDto webhookDto) {
        PayoutInstruction payout = payoutInstructionRepository.findById(webhookDto.getPayoutId())
                .orElseThrow(() -> new ResourceNotFoundException("PayoutInstruction not found with id: " + webhookDto.getPayoutId()));

        payout.setStatus(webhookDto.getStatus());
        if (webhookDto.getProviderReference() != null && !webhookDto.getProviderReference().isBlank()) {
            payout.setProviderReference(webhookDto.getProviderReference());
        }
        if (webhookDto.getFailureReason() != null) {
            payout.setFailureReason(webhookDto.getFailureReason());
        }
        payout.setUpdatedAt(Instant.now());
        payoutInstructionRepository.save(payout);

        log.info("Processed banking webhook for payout {}: new status={}, UTR={}",
                payout.getId(), payout.getStatus(), payout.getProviderReference());
        return toDto(payout);
    }

    public PayoutInstructionResponseDto toDto(PayoutInstruction p) {
        return PayoutInstructionResponseDto.builder()
                .id(p.getId())
                .settlementRunId(p.getSettlementRun().getId())
                .vendorId(p.getVendor().getId())
                .driverId(p.getDriver() != null ? p.getDriver().getId() : null)
                .driverName(p.getDriver() != null ? p.getDriver().getName() : null)
                .driverExternalRef(p.getDriver() != null ? p.getDriver().getExternalRef() : null)
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .idempotencyKey(p.getIdempotencyKey())
                .status(p.getStatus())
                .providerReference(p.getProviderReference())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.dto.BankingWebhookRequestDto;
import com.moveinsync.settlement.dto.OutboxEventResponseDto;
import com.moveinsync.settlement.dto.OutboxProcessResultDto;
import com.moveinsync.settlement.dto.PayoutInstructionResponseDto;
import com.moveinsync.settlement.entity.OutboxEvent;
import com.moveinsync.settlement.payout.task.OutboxProcessorTask;
import com.moveinsync.settlement.repository.OutboxEventRepository;
import com.moveinsync.settlement.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payouts")
@Tag(name = "Payouts & Outbox", description = "Operations for banking disbursement instructions, Dead-Letter Queue and Transactional Outbox worker")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;
    private final OutboxProcessorTask outboxProcessorTask;
    private final OutboxEventRepository outboxEventRepository;

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'VENDOR_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get banking payout instruction by ID")
    public ResponseEntity<PayoutInstructionResponseDto> getPayoutById(@PathVariable Long id) {
        return ResponseEntity.ok(payoutService.getPayoutById(id));
    }

    @GetMapping("/run/{runId}")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'VENDOR_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all banking payout instructions generated for a settlement run")
    public ResponseEntity<List<PayoutInstructionResponseDto>> getPayoutsByRunId(@PathVariable Long runId) {
        return ResponseEntity.ok(payoutService.getPayoutsByRunId(runId));
    }

    @PostMapping("/outbox/process")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Audited(entityType = "OUTBOX", action = "MANUAL_TRIGGER")
    @ExecutionTime
    @Operation(summary = "Manually trigger immediate execution of pending outbox events using SKIP LOCKED")
    public ResponseEntity<OutboxProcessResultDto> triggerOutboxProcessor() {
        log.info("Manual trigger for outbox processor requested via REST");
        return ResponseEntity.ok(outboxProcessorTask.processPendingEvents());
    }

    @GetMapping("/outbox/pending")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all pending outbox events queued for bank dispatch")
    public ResponseEntity<List<OutboxEventResponseDto>> getPendingOutboxEvents() {
        return ResponseEntity.ok(outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING
        ).stream().map(OutboxEventResponseDto::fromEntity).toList());
    }

    @GetMapping("/outbox/failed")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all dead-lettered outbox events (DLQ) requiring ops inspection")
    public ResponseEntity<List<OutboxEventResponseDto>> getFailedOutboxEvents() {
        return ResponseEntity.ok(outboxEventRepository.findByStatusOrderByCreatedAtDesc(
                OutboxEvent.OutboxStatus.FAILED
        ).stream().map(OutboxEventResponseDto::fromEntity).toList());
    }

    @PostMapping("/outbox/retry/{eventId}")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Audited(entityType = "OUTBOX", action = "RETRY_EVENT")
    @Operation(summary = "Replay and retry a specific dead-lettered outbox event from the DLQ")
    public ResponseEntity<Map<String, Object>> retrySingleEvent(@PathVariable Long eventId) {
        boolean success = outboxProcessorTask.retrySingleEvent(eventId);
        return ResponseEntity.ok(Map.of("eventId", eventId, "success", success));
    }

    @PostMapping("/outbox/retry-all")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Audited(entityType = "OUTBOX", action = "RETRY_ALL")
    @Operation(summary = "Replay all dead-lettered outbox events back into PENDING state")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        int count = outboxProcessorTask.retryAllFailedEvents();
        return ResponseEntity.ok(Map.of("requeuedCount", count));
    }

    @PostMapping("/webhook")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Simulate asynchronous bank rail webhook update (RazorpayX/ICICI/IMPS)")
    public ResponseEntity<PayoutInstructionResponseDto> handleBankingWebhook(
            @Valid @RequestBody BankingWebhookRequestDto request
    ) {
        return ResponseEntity.ok(payoutService.handleBankingWebhook(request));
    }
}

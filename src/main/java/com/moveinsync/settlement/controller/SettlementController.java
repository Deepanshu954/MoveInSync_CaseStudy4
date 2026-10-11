package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.common.annotation.Idempotent;
import com.moveinsync.settlement.dto.SettlementCalculateRequestDto;
import com.moveinsync.settlement.dto.SettlementRunResponseDto;
import com.moveinsync.settlement.dto.PayoutFinalizeResponseDto;
import com.moveinsync.settlement.dto.PayoutInstructionResponseDto;
import com.moveinsync.settlement.service.SettlementService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for executing, previewing, retrieving, and approving batch settlement runs.
 *
 * <p>Employs declarative {@link Idempotent} AOP annotation to guarantee Tier-1 HTTP Request
 * idempotency without polluting controller business logic.
 *
 * @author MoveInSync Core Settlement Team
 */
@RestController
@RequestMapping("/api/settlements")
@Tag(name = "Settlements", description = "Operations for batch calculation runs and payslip ledger approval")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Idempotent
    @Operation(summary = "Calculate or preview a settlement run with two-tier idempotency protection")
    public ResponseEntity<SettlementRunResponseDto> calculate(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SettlementCalculateRequestDto request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "ANONYMOUS";
        log.info("REST request to calculate/preview settlement for vendor {} by {}", request.getVendorId(), username);

        SettlementRunResponseDto response = settlementService.calculateOrPreviewSettlement(
                request, idempotencyKey, username
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'VENDOR_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get settlement run details with all driver payslips and itemized lines")
    public ResponseEntity<SettlementRunResponseDto> getSettlementRun(@PathVariable Long id) {
        return ResponseEntity
                .ok(settlementService.getSettlementRun(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Approve a calculated settlement run for payment dispatch")
    public ResponseEntity<SettlementRunResponseDto> approveSettlementRun(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity
                .ok(settlementService.approveSettlementRun(id, username));
    }
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Move a calculated settlement run to finance review")
    public ResponseEntity<SettlementRunResponseDto> moveToReview(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity
                .ok(settlementService.moveToReview(id, username));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Finalize an approved settlement run, generate payout instructions and queue outbox events")
    public ResponseEntity<PayoutFinalizeResponseDto> finalizeSettlementRun(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity
                .ok(settlementService.finalizeSettlementRun(id, username));
    }

    @GetMapping("/{id}/payouts")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'VENDOR_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all banking payout instructions generated for a settlement run")
    public ResponseEntity<List<PayoutInstructionResponseDto>> getRunPayouts(@PathVariable Long id) {
        return ResponseEntity
                .ok(settlementService.getRunPayouts(id));
    }
}

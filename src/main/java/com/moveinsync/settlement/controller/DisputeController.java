package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.DisputeCreateRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveRequestDto;
import com.moveinsync.settlement.dto.DisputeResolveResponseDto;
import com.moveinsync.settlement.dto.DisputeResponseDto;
import com.moveinsync.settlement.service.DisputeService;
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

import java.util.List;

@RestController
@RequestMapping("/api/disputes")
@Tag(name = "Disputes & Delta Settlements", description = "Operations for trip dispute ticketing and closed-loop delta payouts")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Raise a billing dispute ticket against a settled trip")
    public ResponseEntity<DisputeResponseDto> raiseDispute(
            @Valid @RequestBody DisputeCreateRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "DRIVER";
        return ResponseEntity.ok(disputeService.raiseDispute(request, username));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('DRIVER', 'VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get dispute ticket by ID")
    public ResponseEntity<DisputeResponseDto> getDispute(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getDispute(id));
    }

    @GetMapping("/trip/{tripId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List dispute tickets filed for a trip")
    public ResponseEntity<List<DisputeResponseDto>> listDisputesByTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(disputeService.listDisputesByTrip(tripId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all open/unresolved disputes requiring finance approval")
    public ResponseEntity<List<DisputeResponseDto>> listOpenDisputes() {
        return ResponseEntity.ok(disputeService.listOpenDisputes());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Approve or reject a dispute, calculating only the delta and emitting an immediate payout")
    public ResponseEntity<DisputeResolveResponseDto> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeResolveRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity.ok(disputeService.resolveDispute(id, request, username));
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Approve or reject a dispute by body disputeId, calculating only the delta and emitting an immediate payout")
    public ResponseEntity<DisputeResolveResponseDto> resolveDisputeFromBody(
            @Valid @RequestBody DisputeResolveRequestDto request,
            Authentication authentication) {
        if (request.getDisputeId() == null) {
            throw new IllegalArgumentException("disputeId is required in request body when calling /api/disputes/resolve");
        }
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity.ok(disputeService.resolveDispute(request.getDisputeId(), request, username));
    }
}
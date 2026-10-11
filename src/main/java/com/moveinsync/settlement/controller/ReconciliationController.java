package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.common.annotation.ExecutionTime;
import com.moveinsync.settlement.dto.ReconcileRequestDto;
import com.moveinsync.settlement.dto.ReconciliationResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceResponseDto;
import com.moveinsync.settlement.dto.VendorInvoiceSubmitRequestDto;
import com.moveinsync.settlement.service.ReconciliationService;
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
@RequestMapping("/api/reconciliations")
@Tag(name = "Reconciliation", description = "Operations for vendor invoice submissions and three-way tolerance matching")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Submit a vendor invoice for month-end reconciliation")
    public ResponseEntity<VendorInvoiceResponseDto> submitInvoice(
            @Valid @RequestBody VendorInvoiceSubmitRequestDto request) {
        return ResponseEntity.ok(reconciliationService.submitInvoice(request));
    }

        @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List vendor invoices with optional vendor filter")
    public ResponseEntity<List<VendorInvoiceResponseDto>> listInvoices(
            @RequestParam(required = false) Long vendorId) {
        if (vendorId != null) {
            return ResponseEntity.ok(reconciliationService.listInvoicesByVendor(vendorId));
        }
        return ResponseEntity.ok(reconciliationService.listAllInvoices());
    }

    @GetMapping("/invoices/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get vendor invoice by ID")
    public ResponseEntity<VendorInvoiceResponseDto> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.getInvoice(id));
    }

    @GetMapping("/invoices/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all invoices submitted by a vendor")
    public ResponseEntity<List<VendorInvoiceResponseDto>> listVendorInvoices(@PathVariable Long vendorId) {
        return ResponseEntity.ok(reconciliationService.listInvoicesByVendor(vendorId));
    }

    @PostMapping("/compare")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Execute tolerance comparison between vendor invoice and calculated settlement run")
    public ResponseEntity<ReconciliationResponseDto> reconcileInvoice(
            @Valid @RequestBody ReconcileRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "APPROVER";
        return ResponseEntity.ok(reconciliationService.reconcileInvoice(request, username));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyRole('SETTLEMENT_ADMIN', 'APPROVER', 'VENDOR_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get reconciliation audit record by ID")
    public ResponseEntity<ReconciliationResponseDto> getReconciliationRecord(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.getReconciliationRecord(id));
    }
}

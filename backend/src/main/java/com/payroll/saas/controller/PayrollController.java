package com.payroll.saas.controller;

import com.payroll.saas.dto.PayrollPreviewResponse;
import com.payroll.saas.dto.PayrollProcessResponse;
import com.payroll.saas.dto.PayrollResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/company-admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Company payroll", description = "Company-scoped payroll preview, processing, and records.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Insufficient role, tenant mismatch, or suspended company"),
        @ApiResponse(responseCode = "400", description = "Invalid period, incomplete attendance, or invalid salary data")
})
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping("/payroll-periods/{period}/preview")
    @Operation(summary = "Preview payroll for a month before processing")
    public ResponseEntity<PayrollPreviewResponse> preview(
            @PathVariable String period, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.previewPayroll(actor, period));
    }

    @PostMapping("/payroll-periods/{period}/process")
    @Operation(summary = "Process eligible employees for a completed pay period")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payroll records created"),
            @ApiResponse(responseCode = "409", description = "The employee-period payroll already exists")
    })
    public ResponseEntity<PayrollProcessResponse> process(
            @PathVariable String period, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.processPayroll(actor, period));
    }

    @GetMapping("/payrolls")
    @Operation(summary = "List this company's processed payroll")
    public ResponseEntity<List<PayrollResponse>> payrolls(
            @RequestParam(required = false) String payPeriod,
            @RequestParam(required = false) Long companyId,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getCompanyPayrolls(actor, payPeriod, companyId));
    }

    @GetMapping("/payrolls/{payrollId}")
    @Operation(summary = "Get a payroll detail and its saved salary breakdown")
    @ApiResponse(responseCode = "404", description = "Payroll record not found in this company")
    public ResponseEntity<PayrollResponse> payroll(
            @PathVariable Long payrollId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getCompanyPayroll(actor, payrollId));
    }
}

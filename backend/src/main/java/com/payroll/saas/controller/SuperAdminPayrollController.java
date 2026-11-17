package com.payroll.saas.controller;

import com.payroll.saas.dto.PayrollResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin/payrolls")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Super Admin payroll oversight", description = "Read-only platform-wide payroll oversight.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Super Admin role required")
})
public class SuperAdminPayrollController {

    private final PayrollService payrollService;

    @GetMapping
    @Operation(summary = "List payroll records platform-wide or filter by company and period")
    public ResponseEntity<List<PayrollResponse>> payrolls(
            @Parameter(description = "Optional company filter") @RequestParam(required = false) Long companyId,
            @Parameter(description = "Optional YYYY-MM pay-period filter") @RequestParam(required = false) String payPeriod,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getPlatformPayrolls(actor, companyId, payPeriod));
    }

    @GetMapping("/{payrollId}")
    @Operation(summary = "Read an individual payroll record for platform oversight")
    @ApiResponse(responseCode = "404", description = "Payroll record not found")
    public ResponseEntity<PayrollResponse> payroll(
            @PathVariable Long payrollId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getPlatformPayroll(actor, payrollId));
    }
}

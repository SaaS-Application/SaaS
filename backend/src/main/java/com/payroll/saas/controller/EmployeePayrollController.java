package com.payroll.saas.controller;

import com.payroll.saas.dto.PayrollResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/employee/payrolls")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employee payroll", description = "Self-service payroll records for the authenticated employee only.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Employee profile is not linked or company is suspended"),
        @ApiResponse(responseCode = "404", description = "Payroll record not found for the authenticated employee")
})
public class EmployeePayrollController {

    private final PayrollService payrollService;

    @GetMapping
    @Operation(summary = "List the authenticated employee's payroll history")
    public ResponseEntity<List<PayrollResponse>> payrolls(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getOwnPayrolls(actor));
    }

    @GetMapping("/{payrollId}")
    @Operation(summary = "Get an individual payroll record owned by the authenticated employee")
    public ResponseEntity<PayrollResponse> payroll(
            @PathVariable Long payrollId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payrollService.getOwnPayroll(actor, payrollId));
    }
}

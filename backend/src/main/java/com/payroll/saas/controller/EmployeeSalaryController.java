package com.payroll.saas.controller;

import com.payroll.saas.dto.SalaryAssignmentResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.SalaryStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employee/salary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employee salary", description = "Self-service salary views restricted to the authenticated employee's linked profile.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Not an employee or no linked employee profile"),
        @ApiResponse(responseCode = "404", description = "No salary assignment is effective today")
})
public class EmployeeSalaryController {

    private final SalaryStructureService salaryService;

    @GetMapping("/current")
    @Operation(summary = "Get the current salary assignment")
    public ResponseEntity<SalaryAssignmentResponse> current(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.currentEmployeeSalary(actor));
    }

    @GetMapping("/history")
    @Operation(summary = "List current and historical salary assignments")
    public ResponseEntity<List<SalaryAssignmentResponse>> history(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.employeeSalaryHistory(actor));
    }
}

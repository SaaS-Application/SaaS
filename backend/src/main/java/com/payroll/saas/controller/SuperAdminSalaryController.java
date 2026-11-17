package com.payroll.saas.controller;

import com.payroll.saas.dto.SalaryPlatformSummaryResponse;
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

@RestController
@RequestMapping("/api/super-admin/salary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Super Admin salary summary", description = "Read-only aggregate counts across companies. Requires SUPER_ADMIN JWT.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "SUPER_ADMIN role required")
})
public class SuperAdminSalaryController {

    private final SalaryStructureService salaryService;

    @GetMapping("/summary")
    @Operation(summary = "Get platform salary counts without salary details")
    public ResponseEntity<SalaryPlatformSummaryResponse> summary(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.superAdminSummary(actor));
    }
}

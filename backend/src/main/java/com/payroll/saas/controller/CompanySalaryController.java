package com.payroll.saas.controller;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.SalaryStructureService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/company-admin/salary")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Salary management", description = "Company-scoped salary components, structures, and assignments. Requires COMPANY_ADMIN or HR_ADMIN JWT.")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request, calculation, or effective-date range"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Insufficient role, cross-tenant access, or suspended company"),
        @ApiResponse(responseCode = "404", description = "Company-scoped resource not found"),
        @ApiResponse(responseCode = "409", description = "Duplicate code or overlapping salary assignment")
})
public class CompanySalaryController {

    private final SalaryStructureService salaryService;

    @GetMapping("/components")
    @Operation(summary = "List salary components")
    public ResponseEntity<List<SalaryComponentResponse>> components(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.components(actor));
    }

    @PostMapping("/components")
    @Operation(summary = "Create a salary component")
    @ApiResponse(responseCode = "201", description = "Salary component created")
    public ResponseEntity<SalaryComponentResponse> createComponent(
            @Valid @RequestBody SalaryComponentRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryService.createComponent(actor, request));
    }

    @PutMapping("/components/{componentId}")
    @Operation(summary = "Update a salary component")
    public ResponseEntity<SalaryComponentResponse> updateComponent(
            @PathVariable Long componentId, @Valid @RequestBody SalaryComponentRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.updateComponent(actor, componentId, request));
    }

    @PatchMapping("/components/{componentId}/status")
    @Operation(summary = "Activate or deactivate a salary component")
    public ResponseEntity<SalaryComponentResponse> updateComponentStatus(
            @PathVariable Long componentId, @Valid @RequestBody SalaryComponentStatusRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.setComponentActive(actor, componentId, request.getActive()));
    }

    @GetMapping("/structures")
    @Operation(summary = "List salary structures with illustrative breakdowns")
    public ResponseEntity<List<SalaryStructureResponse>> structures(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.structures(actor));
    }

    @PostMapping("/structures")
    @Operation(summary = "Create a salary structure")
    @ApiResponse(responseCode = "201", description = "Salary structure created with an illustrative breakdown")
    public ResponseEntity<SalaryStructureResponse> createStructure(
            @Valid @RequestBody SalaryStructureRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryService.createStructure(actor, request));
    }

    @GetMapping("/structures/{structureId}")
    @Operation(summary = "Get a salary structure and illustrative breakdown")
    public ResponseEntity<SalaryStructureResponse> structure(
            @PathVariable Long structureId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.structure(actor, structureId));
    }

    @PutMapping("/structures/{structureId}")
    @Operation(summary = "Update a salary structure")
    public ResponseEntity<SalaryStructureResponse> updateStructure(
            @PathVariable Long structureId, @Valid @RequestBody SalaryStructureRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.updateStructure(actor, structureId, request));
    }

    @PatchMapping("/structures/{structureId}/status")
    @Operation(summary = "Activate or deactivate a salary structure")
    public ResponseEntity<SalaryStructureResponse> updateStructureStatus(
            @PathVariable Long structureId, @Valid @RequestBody SalaryStructureStatusRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.setStructureActive(actor, structureId, request.getActive()));
    }

    @PostMapping("/assignments")
    @Operation(summary = "Assign a structure to an employee with effective dates")
    @ApiResponse(responseCode = "201", description = "Salary assignment created and history retained")
    public ResponseEntity<SalaryAssignmentResponse> assign(
            @Valid @RequestBody SalaryAssignmentRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salaryService.assign(actor, request));
    }

    @GetMapping("/employees/{employeeId}/assignments")
    @Operation(summary = "List an employee's salary assignment history")
    public ResponseEntity<List<SalaryAssignmentResponse>> employeeAssignments(
            @PathVariable Long employeeId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(salaryService.employeeAssignments(actor, employeeId));
    }
}

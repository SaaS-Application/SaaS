package com.payroll.saas.controller;

import com.payroll.saas.dto.EmployeeRequest;
import com.payroll.saas.dto.EmployeeResponse;
import com.payroll.saas.dto.EmployeeAccountLinkRequest;
import com.payroll.saas.dto.EmployeeStatusRequest;
import com.payroll.saas.dto.EmployeeSummaryResponse;
import com.payroll.saas.dto.OrganizationUnitRequest;
import com.payroll.saas.dto.OrganizationUnitResponse;
import com.payroll.saas.dto.OrganizationUnitStatusRequest;
import com.payroll.saas.dto.PageResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.User;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.service.CompanyService;
import com.payroll.saas.service.DepartmentService;
import com.payroll.saas.service.DesignationService;
import com.payroll.saas.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company-admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR_ADMIN')")
public class CompanyAdminController {

    private final EmployeeService employeeService;
    private final CompanyService companyService;
    private final DepartmentService departmentService;
    private final DesignationService designationService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(@AuthenticationPrincipal User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", user.getRole().name());
        payload.put("companyName", user.getCompany() == null ? null : user.getCompany().getName());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getCompanySettings(
            @RequestParam(required = false) Long companyId,
            @AuthenticationPrincipal User user) {
        Company company = ownedCompany(user, companyId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("companyId", company.getId());
        payload.put("companyName", company.getName());
        payload.put("status", company.getStatus());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/employees")
    public ResponseEntity<PageResponse<EmployeeSummaryResponse>> getEmployees(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        validatePage(page, size);
        ownedCompany(user, companyId);
        return ResponseEntity.ok(PageResponse.from(employeeService.searchEmployees(
                user, search, departmentId, designationId, employmentType, status, page, size), item -> item));
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(request, user));
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeResponse> getEmployee(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.getEmployee(user, employeeId));
    }

    @PutMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.updateEmployee(user, employeeId, request));
    }

    @PatchMapping("/employees/{employeeId}/status")
    public ResponseEntity<EmployeeResponse> updateEmployeeStatus(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeStatusRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.updateStatus(user, employeeId, request.getStatus()));
    }

    @PostMapping("/employees/{employeeId}/account")
    public ResponseEntity<Void> linkEmployeeAccount(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeAccountLinkRequest request,
            @AuthenticationPrincipal User user) {
        employeeService.linkLoginAccount(user, employeeId, request.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments")
    public ResponseEntity<List<OrganizationUnitResponse>> getDepartments(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(departmentService.list(user));
    }

    @PostMapping("/departments")
    public ResponseEntity<OrganizationUnitResponse> createDepartment(
            @Valid @RequestBody OrganizationUnitRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.create(user, request));
    }

    @PutMapping("/departments/{departmentId}")
    public ResponseEntity<OrganizationUnitResponse> updateDepartment(
            @PathVariable Long departmentId,
            @Valid @RequestBody OrganizationUnitRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(departmentService.update(user, departmentId, request));
    }

    @PatchMapping("/departments/{departmentId}/status")
    public ResponseEntity<OrganizationUnitResponse> updateDepartmentStatus(
            @PathVariable Long departmentId,
            @Valid @RequestBody OrganizationUnitStatusRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(departmentService.setActive(user, departmentId, request.getActive()));
    }

    @GetMapping("/designations")
    public ResponseEntity<List<OrganizationUnitResponse>> getDesignations(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(designationService.list(user));
    }

    @PostMapping("/designations")
    public ResponseEntity<OrganizationUnitResponse> createDesignation(
            @Valid @RequestBody OrganizationUnitRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(designationService.create(user, request));
    }

    @PutMapping("/designations/{designationId}")
    public ResponseEntity<OrganizationUnitResponse> updateDesignation(
            @PathVariable Long designationId,
            @Valid @RequestBody OrganizationUnitRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(designationService.update(user, designationId, request));
    }

    @PatchMapping("/designations/{designationId}/status")
    public ResponseEntity<OrganizationUnitResponse> updateDesignationStatus(
            @PathVariable Long designationId,
            @Valid @RequestBody OrganizationUnitStatusRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(designationService.setActive(user, designationId, request.getActive()));
    }

    private Company ownedCompany(User user, Long requestedCompanyId) {
        if (user.getCompany() == null) {
            throw new ApiException("No company context for this user");
        }
        Long ownCompanyId = user.getCompany().getId();
        if (requestedCompanyId != null && !ownCompanyId.equals(requestedCompanyId)) {
            return companyService.validateCompanyOwnership(user, requestedCompanyId);
        }
        return companyService.validateCompanyOwnership(user, ownCompanyId);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        }
    }
}

package com.payroll.saas.controller;

import com.payroll.saas.dto.CompanyRegistrationRequest;
import com.payroll.saas.dto.CompanySummaryResponse;
import com.payroll.saas.dto.EmployeeSummaryResponse;
import com.payroll.saas.dto.PageResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.CompanyService;
import com.payroll.saas.service.DashboardService;
import com.payroll.saas.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final DashboardService dashboardService;
    private final CompanyService companyService;
    private final EmployeeService employeeService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(dashboardService.getPlatformDashboard());
    }

    @GetMapping("/companies")
    public ResponseEntity<List<CompanySummaryResponse>> getCompanies() {
        return ResponseEntity.ok(companyService.getAllCompanies().stream()
                .map(CompanySummaryResponse::from)
                .toList());
    }

    @PostMapping("/companies")
    public ResponseEntity<Company> registerCompany(@Valid @RequestBody CompanyRegistrationRequest request) {
        return ResponseEntity.ok(companyService.registerCompany(request));
    }

    @PatchMapping("/companies/{companyId}/status")
    public ResponseEntity<Map<String, String>> updateCompanyStatus(
            @PathVariable Long companyId,
            @RequestParam String status,
            @AuthenticationPrincipal User currentUser) {
        Company company = companyService.updateCompanyStatus(companyId, status, currentUser);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Company status updated");
        response.put("companyCode", company.getCode());
        response.put("status", company.getStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/companies/{companyId}/admins")
    public ResponseEntity<List<User>> getCompanyAdmins(@PathVariable Long companyId) {
        return ResponseEntity.ok(companyService.getCompanyAdmins(companyId));
    }

    @GetMapping("/companies/{companyId}/employees")
    public ResponseEntity<PageResponse<EmployeeSummaryResponse>> getCompanyEmployees(
            @PathVariable Long companyId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return ResponseEntity.ok(PageResponse.from(employeeService.searchCompanyEmployees(
                user, companyId, search, departmentId, designationId, employmentType, status, page, size), item -> item));
    }

    @GetMapping("/companies/{companyId}/employees/{employeeId}")
    public ResponseEntity<EmployeeSummaryResponse> getCompanyEmployee(
            @PathVariable Long companyId,
            @PathVariable Long employeeId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.getCompanyEmployee(user, companyId, employeeId));
    }
}

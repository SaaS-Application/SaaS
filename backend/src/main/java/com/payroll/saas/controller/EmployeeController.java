package com.payroll.saas.controller;

import com.payroll.saas.dto.EmployeeResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employee")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR_ADMIN','EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myProfile(@AuthenticationPrincipal User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EmployeeResponse> ownEmployeeProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.getOwnEmployeeProfile(user));
    }

    @GetMapping("/records/{employeeId}")
    public ResponseEntity<EmployeeResponse> getEmployeeRecord(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Long companyId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(employeeService.getEmployeeRecord(user, employeeId, companyId));
    }
}

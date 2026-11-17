package com.payroll.saas.controller;

import com.payroll.saas.dto.LeaveOverviewResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.LeaveManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/super-admin/companies/{companyId}/leave")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminLeaveController {

    private final LeaveManagementService leaveManagementService;

    @GetMapping("/overview")
    public ResponseEntity<LeaveOverviewResponse> overview(
            @PathVariable Long companyId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.superAdminOverview(actor, companyId));
    }
}

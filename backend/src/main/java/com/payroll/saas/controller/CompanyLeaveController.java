package com.payroll.saas.controller;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.LeaveRequestStatus;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.LeaveManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/company-admin/leave")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR_ADMIN')")
public class CompanyLeaveController {

    private final LeaveManagementService leaveManagementService;

    @GetMapping("/overview")
    public ResponseEntity<LeaveOverviewResponse> overview(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyOverview(actor));
    }

    @GetMapping("/types")
    public ResponseEntity<List<LeaveTypeResponse>> types(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyLeaveTypes(actor));
    }

    @PostMapping("/types")
    public ResponseEntity<LeaveTypeResponse> createType(
            @Valid @RequestBody LeaveTypeRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveManagementService.createLeaveType(actor, request));
    }

    @PutMapping("/types/{typeId}")
    public ResponseEntity<LeaveTypeResponse> updateType(
            @PathVariable Long typeId, @Valid @RequestBody LeaveTypeRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.updateLeaveType(actor, typeId, request));
    }

    @PatchMapping("/types/{typeId}/status")
    public ResponseEntity<LeaveTypeResponse> setTypeStatus(
            @PathVariable Long typeId, @Valid @RequestBody LeaveTypeStatusRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.setLeaveTypeActive(actor, typeId, request.getActive()));
    }

    @GetMapping("/holidays")
    public ResponseEntity<List<CompanyHolidayResponse>> holidays(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyHolidays(actor));
    }

    @PostMapping("/holidays")
    public ResponseEntity<CompanyHolidayResponse> createHoliday(
            @Valid @RequestBody CompanyHolidayRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveManagementService.createHoliday(actor, request));
    }

    @PutMapping("/holidays/{holidayId}")
    public ResponseEntity<CompanyHolidayResponse> updateHoliday(
            @PathVariable Long holidayId, @Valid @RequestBody CompanyHolidayRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.updateHoliday(actor, holidayId, request));
    }

    @PatchMapping("/holidays/{holidayId}/status")
    public ResponseEntity<CompanyHolidayResponse> setHolidayStatus(
            @PathVariable Long holidayId, @Valid @RequestBody CompanyHolidayStatusRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.setHolidayActive(actor, holidayId, request.getActive()));
    }

    @GetMapping("/applications")
    public ResponseEntity<PageResponse<LeaveApplicationResponse>> applications(
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Page<LeaveApplicationResponse> result = leaveManagementService.companyApplications(
                actor, status, employeeId, leaveTypeId, from, to, departmentId, designationId, search, page, size);
        return ResponseEntity.ok(PageResponse.from(result, item -> item));
    }

    @GetMapping("/applications/{requestId}")
    public ResponseEntity<LeaveApplicationResponse> application(
            @PathVariable Long requestId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyApplication(actor, requestId));
    }

    @PatchMapping("/applications/{requestId}/decision")
    public ResponseEntity<LeaveApplicationResponse> decide(
            @PathVariable Long requestId, @Valid @RequestBody LeaveDecisionRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.decide(actor, requestId, request));
    }

    @PatchMapping("/applications/{requestId}/cancellation")
    public ResponseEntity<LeaveApplicationResponse> decideCancellation(
            @PathVariable Long requestId, @Valid @RequestBody LeaveCancellationDecisionRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.decideCancellation(actor, requestId, request));
    }

    @GetMapping("/employees/{employeeId}/balances")
    public ResponseEntity<List<LeaveBalanceResponse>> employeeBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyEmployeeBalances(
                actor, employeeId, year == null ? LocalDate.now().getYear() : year));
    }

    @PostMapping("/balances")
    public ResponseEntity<LeaveBalanceResponse> allocateBalance(
            @Valid @RequestBody LeaveBalanceAllocationRequest request, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.allocateBalance(actor, request));
    }

    @GetMapping("/reports")
    public ResponseEntity<LeaveOverviewResponse> reports(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.companyOverview(actor));
    }

    @GetMapping("/applications/{requestId}/document")
    public ResponseEntity<byte[]> document(
            @PathVariable Long requestId, @AuthenticationPrincipal User actor) {
        LeaveManagementService.DocumentDownload file = leaveManagementService.document(actor, requestId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leave-supporting-document\"")
                .body(file.content());
    }
}

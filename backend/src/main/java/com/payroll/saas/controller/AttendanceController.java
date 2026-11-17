package com.payroll.saas.controller;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.AttendanceStatus;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/company-admin/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR_ADMIN')")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/daily")
    public ResponseEntity<PageResponse<AttendanceRecordResponse>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User actor) {
        Page<AttendanceRecordResponse> records = attendanceService.daily(actor, date, search, departmentId, status, page, size);
        return ResponseEntity.ok(PageResponse.from(records, item -> item));
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<List<AttendanceRecordResponse>> employeeHistory(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.employeeHistory(actor, employeeId, from, to));
    }

    @GetMapping("/monthly")
    public ResponseEntity<AttendanceMonthlySummaryResponse> monthly(
            @RequestParam String month,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.companyMonthly(actor, month));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<AttendanceEmployeeReportResponse>> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.companyReport(actor, from, to, departmentId));
    }

    @PostMapping("/manual")
    public ResponseEntity<AttendanceRecordResponse> manual(
            @Valid @RequestBody ManualAttendanceRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.createManual(actor, request));
    }

    @PutMapping("/{attendanceId}/correction")
    public ResponseEntity<AttendanceRecordResponse> correct(
            @PathVariable Long attendanceId,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.correct(actor, attendanceId, request));
    }

    @PatchMapping("/{attendanceId}/approval")
    public ResponseEntity<AttendanceRecordResponse> decide(
            @PathVariable Long attendanceId,
            @Valid @RequestBody AttendanceApprovalRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.decideApproval(actor, attendanceId, request));
    }

    @GetMapping("/rules")
    public ResponseEntity<AttendanceRuleResponse> rules(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.getRules(actor));
    }

    @PutMapping("/rules")
    public ResponseEntity<AttendanceRuleResponse> updateRules(
            @Valid @RequestBody AttendanceRuleRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.updateRules(actor, request));
    }
}

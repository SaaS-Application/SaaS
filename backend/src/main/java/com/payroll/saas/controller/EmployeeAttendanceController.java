package com.payroll.saas.controller;

import com.payroll.saas.dto.AttendanceMonthlySummaryResponse;
import com.payroll.saas.dto.AttendanceNoteRequest;
import com.payroll.saas.dto.AttendanceRecordResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employee/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceRecordResponse> checkIn(
            @Valid @RequestBody(required = false) AttendanceNoteRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.checkIn(actor, request == null ? null : request.getNotes()));
    }

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceRecordResponse> checkOut(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.checkOut(actor));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AttendanceRecordResponse>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.ownHistory(actor, from, to));
    }

    @GetMapping("/monthly")
    public ResponseEntity<AttendanceMonthlySummaryResponse> monthly(
            @RequestParam String month,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(attendanceService.ownMonthly(actor, month));
    }
}

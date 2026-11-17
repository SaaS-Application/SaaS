package com.payroll.saas.controller;

import com.payroll.saas.dto.*;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employee/leave")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeLeaveController {

    private final LeaveManagementService leaveManagementService;

    @GetMapping("/types")
    public ResponseEntity<List<LeaveTypeResponse>> types(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.employeeLeaveTypes(actor));
    }

    @GetMapping("/balances")
    public ResponseEntity<List<LeaveBalanceResponse>> balances(
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.employeeBalances(
                actor, year == null ? LocalDate.now().getYear() : year));
    }

    @PostMapping("/applications")
    public ResponseEntity<LeaveApplicationResponse> apply(
            @Valid @RequestBody LeaveApplicationRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveManagementService.apply(actor, request, null));
    }

    @PostMapping(value = "/applications/with-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeaveApplicationResponse> applyWithDocument(
            @Valid @RequestPart("request") LeaveApplicationRequest request,
            @RequestPart("document") MultipartFile document,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveManagementService.apply(actor, request, document));
    }

    @GetMapping("/applications")
    public ResponseEntity<PageResponse<LeaveApplicationResponse>> applications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Page<LeaveApplicationResponse> result = leaveManagementService.employeeApplications(actor, page, size);
        return ResponseEntity.ok(PageResponse.from(result, item -> item));
    }

    @GetMapping("/applications/{requestId}")
    public ResponseEntity<LeaveApplicationResponse> application(
            @PathVariable Long requestId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.employeeApplication(actor, requestId));
    }

    @PatchMapping("/applications/{requestId}/withdraw")
    public ResponseEntity<LeaveApplicationResponse> withdraw(
            @PathVariable Long requestId, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.withdraw(actor, requestId));
    }

    @PatchMapping("/applications/{requestId}/cancellation")
    public ResponseEntity<LeaveApplicationResponse> requestCancellation(
            @PathVariable Long requestId,
            @Valid @RequestBody LeaveCancellationRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(leaveManagementService.requestCancellation(actor, requestId, request.getComment()));
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

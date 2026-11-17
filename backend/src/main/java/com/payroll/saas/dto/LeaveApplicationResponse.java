package com.payroll.saas.dto;

import com.payroll.saas.entity.LeaveRequest;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class LeaveApplicationResponse {
    Long id;
    Long employeeId;
    String employeeName;
    String employeeNumber;
    Long leaveTypeId;
    String leaveType;
    boolean paid;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal days;
    boolean halfDay;
    String halfDayPeriod;
    String status;
    String reason;
    String reviewComment;
    String cancellationComment;
    boolean supportingDocumentAvailable;
    LocalDateTime createdAt;
    LocalDateTime reviewedAt;

    public static LeaveApplicationResponse from(LeaveRequest request, String employeeName, String employeeNumber) {
        return LeaveApplicationResponse.builder().id(request.getId()).employeeId(request.getEmployeeId())
                .employeeName(employeeName).employeeNumber(employeeNumber).leaveTypeId(request.getLeaveTypeId())
                .leaveType(request.getLeaveType()).paid(request.isPaid()).startDate(request.getStartDate())
                .endDate(request.getEndDate()).days(request.getDays()).halfDay(request.isHalfDay())
                .halfDayPeriod(request.getHalfDayPeriod() == null ? null : request.getHalfDayPeriod().name())
                .status(request.getStatus().name()).reason(request.getReason())
                .reviewComment(request.getReviewComment()).cancellationComment(request.getCancellationComment())
                .supportingDocumentAvailable(request.getSupportingDocumentKey() != null)
                .createdAt(request.getCreatedAt()).reviewedAt(request.getReviewedAt()).build();
    }
}

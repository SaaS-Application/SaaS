package com.payroll.saas.dto;

import com.payroll.saas.entity.Attendance;
import com.payroll.saas.entity.Employee;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class AttendanceRecordResponse {
    Long id;
    Long employeeId;
    String employeeNumber;
    String employeeName;
    LocalDate date;
    LocalDateTime checkInTime;
    Instant checkInInstant;
    LocalDateTime checkOutTime;
    Instant checkOutInstant;
    String timeZoneId;
    int totalWorkingMinutes;
    String status;
    boolean lateArrival;
    boolean earlyDeparture;
    int overtimeMinutes;
    String notes;
    String approvalStatus;
    Long createdBy;
    Long updatedBy;
    Long approvedBy;

    public static AttendanceRecordResponse from(Attendance attendance, Employee employee) {
        return AttendanceRecordResponse.builder()
                .id(attendance.getId())
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .date(attendance.getDate())
                .checkInTime(attendance.getCheckInTime())
                .checkInInstant(attendance.getCheckInInstant())
                .checkOutTime(attendance.getCheckOutTime())
                .checkOutInstant(attendance.getCheckOutInstant())
                .timeZoneId(attendance.getTimeZoneId())
                .totalWorkingMinutes(attendance.getTotalWorkingMinutes())
                .status(attendance.getStatus().name())
                .lateArrival(attendance.isLateArrival())
                .earlyDeparture(attendance.isEarlyDeparture())
                .overtimeMinutes(attendance.getOvertimeMinutes())
                .notes(attendance.getNotes())
                .approvalStatus(attendance.getApprovalStatus().name())
                .createdBy(attendance.getCreatedBy())
                .updatedBy(attendance.getUpdatedBy())
                .approvedBy(attendance.getApprovedBy())
                .build();
    }
}

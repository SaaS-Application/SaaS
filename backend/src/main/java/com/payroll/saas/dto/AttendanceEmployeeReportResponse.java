package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AttendanceEmployeeReportResponse {
    Long employeeId;
    String employeeNumber;
    String employeeName;
    int recordedDays;
    int presentDays;
    int absentDays;
    int halfDays;
    int lateArrivals;
    int leaveDays;
    int totalWorkingMinutes;
    int overtimeMinutes;
}

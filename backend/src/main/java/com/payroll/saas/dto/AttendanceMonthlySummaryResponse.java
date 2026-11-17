package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;

@Value
@Builder
public class AttendanceMonthlySummaryResponse {
    YearMonth month;
    int presentDays;
    int absentDays;
    int halfDays;
    int lateDays;
    int lateArrivals;
    int leaveDays;
    int holidayDays;
    int weekOffDays;
    int totalWorkingMinutes;
    int overtimeMinutes;
}

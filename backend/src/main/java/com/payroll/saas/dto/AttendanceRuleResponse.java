package com.payroll.saas.dto;

import com.payroll.saas.entity.AttendanceRule;
import lombok.Builder;
import lombok.Value;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@Value
@Builder
public class AttendanceRuleResponse {
    LocalTime workStartTime;
    LocalTime workEndTime;
    int requiredWorkingMinutes;
    int gracePeriodMinutes;
    int halfDayThresholdMinutes;
    int overtimeThresholdMinutes;
    List<String> weeklyHolidays;
    String timeZoneId;

    public static AttendanceRuleResponse from(AttendanceRule rule) {
        return AttendanceRuleResponse.builder()
                .workStartTime(rule.getWorkStartTime())
                .workEndTime(rule.getWorkEndTime())
                .requiredWorkingMinutes(rule.getRequiredWorkingMinutes())
                .gracePeriodMinutes(rule.getGracePeriodMinutes())
                .halfDayThresholdMinutes(rule.getHalfDayThresholdMinutes())
                .overtimeThresholdMinutes(rule.getOvertimeThresholdMinutes())
                .weeklyHolidays(Arrays.stream(rule.getWeeklyHolidays().split(","))
                        .filter(day -> !day.isBlank()).toList())
                .timeZoneId(rule.getTimeZoneId())
                .build();
    }
}

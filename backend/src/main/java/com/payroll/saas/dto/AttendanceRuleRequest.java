package com.payroll.saas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class AttendanceRuleRequest {
    @NotNull
    private LocalTime workStartTime;

    @NotNull
    private LocalTime workEndTime;

    @Min(1)
    @Max(1440)
    private int requiredWorkingMinutes;

    @Min(0)
    @Max(1439)
    private int gracePeriodMinutes;

    @Min(1)
    @Max(1440)
    private int halfDayThresholdMinutes;

    @Min(0)
    @Max(1440)
    private int overtimeThresholdMinutes;

    @NotNull
    @Size(max = 7)
    private List<@NotBlank String> weeklyHolidays;

    @NotBlank
    @Size(max = 64)
    private String timeZoneId;
}

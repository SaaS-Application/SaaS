package com.payroll.saas.dto;

import com.payroll.saas.entity.LeaveHalfDayPeriod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveApplicationRequest {
    @NotNull
    private Long leaveTypeId;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    private boolean halfDay;
    private LeaveHalfDayPeriod halfDayPeriod;
    @NotBlank @Size(max = 2000)
    private String reason;
}

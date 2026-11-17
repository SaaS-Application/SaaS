package com.payroll.saas.dto;

import com.payroll.saas.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ManualAttendanceRequest {
    @NotNull
    private Long employeeId;

    @NotNull
    private LocalDate date;

    @NotNull
    private AttendanceStatus status;

    private LocalTime checkInTime;
    private LocalTime checkOutTime;

    @Size(max = 1000)
    private String notes;
}

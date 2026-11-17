package com.payroll.saas.dto;

import com.payroll.saas.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class AttendanceCorrectionRequest {
    @NotNull
    private AttendanceStatus status;

    private LocalTime checkInTime;
    private LocalTime checkOutTime;

    @Size(max = 1000)
    private String notes;
}

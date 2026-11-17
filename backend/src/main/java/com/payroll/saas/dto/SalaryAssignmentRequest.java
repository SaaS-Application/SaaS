package com.payroll.saas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SalaryAssignmentRequest {
    @NotNull
    private Long employeeId;
    @NotNull
    private Long salaryStructureId;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

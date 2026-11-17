package com.payroll.saas.dto;

import com.payroll.saas.entity.SalaryCalculationMethod;
import com.payroll.saas.entity.SalaryComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SalaryComponentRequest {
    @NotBlank @Size(max = 32)
    private String code;
    @NotBlank @Size(max = 120)
    private String name;
    @NotNull
    private SalaryComponentType type;
    @NotNull
    private SalaryCalculationMethod calculationMethod;
    private boolean taxable;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

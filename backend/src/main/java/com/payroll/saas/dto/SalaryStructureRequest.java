package com.payroll.saas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SalaryStructureRequest {
    @NotBlank @Size(max = 32)
    private String code;
    @NotBlank @Size(max = 120)
    private String name;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @NotEmpty @Valid
    private List<SalaryStructureComponentRequest> components;
}

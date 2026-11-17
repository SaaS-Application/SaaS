package com.payroll.saas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyHolidayRequest {
    @NotBlank @Size(max = 120)
    private String name;
    @NotNull
    private LocalDate date;
    @Size(max = 500)
    private String description;
}

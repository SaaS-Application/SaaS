package com.payroll.saas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeStatusRequest {
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String status;
}

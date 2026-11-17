package com.payroll.saas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SalaryStructureStatusRequest {
    @NotNull
    private Boolean active;
}

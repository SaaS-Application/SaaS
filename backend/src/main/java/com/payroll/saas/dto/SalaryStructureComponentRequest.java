package com.payroll.saas.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalaryStructureComponentRequest {
    @NotNull
    private Long componentId;
    @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4)
    private BigDecimal value;
    private Long percentageBaseComponentId;
}

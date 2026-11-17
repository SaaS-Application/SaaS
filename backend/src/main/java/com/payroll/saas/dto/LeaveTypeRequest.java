package com.payroll.saas.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LeaveTypeRequest {
    @NotBlank @Size(max = 24)
    private String code;
    @NotBlank @Size(max = 120)
    private String name;
    private boolean paid;
    @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal annualEntitlement;
    @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal carryForwardLimit;
    @Min(1) @Max(366)
    private int maxConsecutiveDays;
    private boolean supportingDocumentRequired;
    private boolean allowPastApplications;
    private boolean allowHalfDay;
    private boolean allowNegativeBalance;
}

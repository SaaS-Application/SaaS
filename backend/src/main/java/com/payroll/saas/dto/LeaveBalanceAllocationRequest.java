package com.payroll.saas.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LeaveBalanceAllocationRequest {
    @NotNull private Long employeeId;
    @NotNull private Long leaveTypeId;
    @Min(2000) @Max(2200) private int leaveYear;
    @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal openingBalance;
    @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal annualEntitlement;
    @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal carryForward;
    @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    private BigDecimal accrued;
    @Size(max = 500)
    private String comment;
}

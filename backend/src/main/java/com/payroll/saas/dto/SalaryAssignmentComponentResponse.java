package com.payroll.saas.dto;

import com.payroll.saas.entity.SalaryCalculationMethod;
import com.payroll.saas.entity.SalaryComponentType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SalaryAssignmentComponentResponse {
    String code;
    String name;
    SalaryComponentType type;
    SalaryCalculationMethod calculationMethod;
    BigDecimal value;
    String percentageBaseCode;
    boolean taxable;
    BigDecimal calculatedAmount;
}

package com.payroll.saas.dto;

import com.payroll.saas.entity.SalaryComponentType;

import java.math.BigDecimal;

public record PayrollComponentResponse(
        String code,
        String name,
        SalaryComponentType type,
        BigDecimal monthlyAmount,
        BigDecimal payableAmount) {
}

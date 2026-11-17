package com.payroll.saas.dto;

import java.math.BigDecimal;
import java.util.List;

public record PayrollEmployeePreviewResponse(
        Long employeeId,
        String employeeNumber,
        String employeeName,
        Long existingPayrollId,
        BigDecimal totalWorkingDays,
        BigDecimal paidDays,
        BigDecimal unpaidDays,
        BigDecimal grossEarnings,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        boolean eligible,
        boolean alreadyProcessed,
        List<String> warnings,
        List<PayrollComponentResponse> components) {
}

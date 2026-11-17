package com.payroll.saas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PayrollResponse(
        Long id,
        Long companyId,
        String payPeriod,
        Long employeeId,
        String employeeNumber,
        String employeeName,
        BigDecimal totalWorkingDays,
        BigDecimal paidDays,
        BigDecimal unpaidDays,
        BigDecimal grossEarnings,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        String status,
        LocalDateTime createdAt,
        LocalDate processedOn,
        List<PayrollComponentResponse> components) {
}

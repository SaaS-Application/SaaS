package com.payroll.saas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayslipSummaryResponse(
        Long payrollId,
        String payPeriod,
        String employeeName,
        String employeeNumber,
        BigDecimal grossEarnings,
        BigDecimal totalDeductions,
        BigDecimal netSalary,
        LocalDate processedOn,
        boolean downloadable,
        boolean profileSnapshotAvailable) {
}

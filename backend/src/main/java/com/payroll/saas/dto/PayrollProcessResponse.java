package com.payroll.saas.dto;

import java.util.List;

public record PayrollProcessResponse(
        String payPeriod,
        int processedCount,
        List<PayrollResponse> payrolls) {
}

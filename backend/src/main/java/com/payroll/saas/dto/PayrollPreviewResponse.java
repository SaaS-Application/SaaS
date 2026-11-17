package com.payroll.saas.dto;

import java.util.List;

public record PayrollPreviewResponse(
        String payPeriod,
        int eligibleCount,
        int skippedCount,
        boolean processingAllowed,
        List<String> warnings,
        List<String> blockingErrors,
        List<PayrollEmployeePreviewResponse> employees) {
}

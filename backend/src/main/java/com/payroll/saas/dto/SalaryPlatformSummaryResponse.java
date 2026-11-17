package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SalaryPlatformSummaryResponse {
    long companies;
    long activeStructures;
    long activeAssignments;
    List<SalaryCompanySummaryResponse> companySummaries;
}

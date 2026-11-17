package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SalaryCompanySummaryResponse {
    Long companyId;
    String companyName;
    String companyCode;
    long activeStructures;
    long activeAssignments;
}

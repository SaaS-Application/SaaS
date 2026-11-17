package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class SalaryAssignmentResponse {
    Long id;
    String structureCode;
    String structureName;
    LocalDate effectiveFrom;
    LocalDate effectiveTo;
    boolean active;
    BigDecimal grossEarnings;
    BigDecimal totalDeductions;
    BigDecimal illustrativeNet;
    List<SalaryAssignmentComponentResponse> components;
}

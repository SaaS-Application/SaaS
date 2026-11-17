package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class SalaryStructureResponse {
    Long id;
    String code;
    String name;
    LocalDate effectiveFrom;
    LocalDate effectiveTo;
    boolean active;
    List<SalaryStructureComponentResponse> components;
    BigDecimal grossEarnings;
    BigDecimal totalDeductions;
    BigDecimal illustrativeNet;
}

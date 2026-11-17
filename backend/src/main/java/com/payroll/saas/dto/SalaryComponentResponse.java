package com.payroll.saas.dto;

import com.payroll.saas.entity.SalaryComponentDefinition;
import com.payroll.saas.entity.SalaryCalculationMethod;
import com.payroll.saas.entity.SalaryComponentType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class SalaryComponentResponse {
    Long id;
    String code;
    String name;
    SalaryComponentType type;
    SalaryCalculationMethod calculationMethod;
    boolean taxable;
    boolean active;
    LocalDate effectiveFrom;
    LocalDate effectiveTo;

    public static SalaryComponentResponse from(SalaryComponentDefinition component) {
        return SalaryComponentResponse.builder().id(component.getId()).code(component.getCode())
                .name(component.getName()).type(component.getType())
                .calculationMethod(component.getCalculationMethod()).taxable(component.isTaxable())
                .active(component.isActive()).effectiveFrom(component.getEffectiveFrom())
                .effectiveTo(component.getEffectiveTo()).build();
    }
}

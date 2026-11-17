package com.payroll.saas.dto;

import com.payroll.saas.entity.LeaveType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class LeaveTypeResponse {
    Long id;
    String code;
    String name;
    boolean paid;
    BigDecimal annualEntitlement;
    BigDecimal carryForwardLimit;
    int maxConsecutiveDays;
    boolean supportingDocumentRequired;
    boolean allowPastApplications;
    boolean allowHalfDay;
    boolean allowNegativeBalance;
    boolean active;

    public static LeaveTypeResponse from(LeaveType type) {
        return LeaveTypeResponse.builder()
                .id(type.getId()).code(type.getCode()).name(type.getName()).paid(type.isPaid())
                .annualEntitlement(type.getAnnualEntitlement()).carryForwardLimit(type.getCarryForwardLimit())
                .maxConsecutiveDays(type.getMaxConsecutiveDays())
                .supportingDocumentRequired(type.isSupportingDocumentRequired())
                .allowPastApplications(type.isAllowPastApplications()).allowHalfDay(type.isAllowHalfDay())
                .allowNegativeBalance(type.isAllowNegativeBalance()).active(type.isActive()).build();
    }
}

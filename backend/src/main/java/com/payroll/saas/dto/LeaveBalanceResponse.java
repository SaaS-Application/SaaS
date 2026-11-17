package com.payroll.saas.dto;

import com.payroll.saas.entity.LeaveBalance;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class LeaveBalanceResponse {
    Long employeeId;
    Long leaveTypeId;
    String leaveTypeCode;
    String leaveTypeName;
    boolean paid;
    int leaveYear;
    BigDecimal openingBalance;
    BigDecimal annualEntitlement;
    BigDecimal carryForward;
    BigDecimal accrued;
    BigDecimal used;
    BigDecimal pending;
    BigDecimal available;
    BigDecimal lopDays;

    public static LeaveBalanceResponse from(LeaveBalance balance, String code, String name, boolean paid,
                                           BigDecimal pending) {
        BigDecimal available = balance.getOpeningBalance().add(balance.getAnnualEntitlement())
                .add(balance.getCarryForward()).add(balance.getAccrued());
        return LeaveBalanceResponse.builder().employeeId(balance.getEmployeeId())
                .leaveTypeId(balance.getLeaveTypeId()).leaveTypeCode(code).leaveTypeName(name).paid(paid)
                .leaveYear(balance.getLeaveYear()).openingBalance(balance.getOpeningBalance())
                .annualEntitlement(balance.getAnnualEntitlement()).carryForward(balance.getCarryForward())
                .accrued(balance.getAccrued()).used(balance.getPaidUsed()).pending(pending)
                .available(paid ? available.subtract(balance.getPaidUsed()) : BigDecimal.ZERO)
                .lopDays(balance.getLopUsed()).build();
    }
}

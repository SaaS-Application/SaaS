package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_balance_employee_type_year",
                columnNames = {"employee_id", "leave_type_id", "leave_year"}),
        @UniqueConstraint(name = "uk_leave_balance_id_employee_company_tenant",
                columnNames = {"id", "employee_id", "company_id", "tenant_id"})
}, indexes = {
        @Index(name = "idx_leave_balance_company", columnList = "company_id,tenant_id,leave_year")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Column(name = "opening_balance", nullable = false, precision = 7, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "annual_entitlement", nullable = false, precision = 7, scale = 2)
    private BigDecimal annualEntitlement;

    @Column(name = "carry_forward", nullable = false, precision = 7, scale = 2)
    private BigDecimal carryForward;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal accrued;

    @Column(name = "paid_used", nullable = false, precision = 7, scale = 2)
    private BigDecimal paidUsed;

    @Column(name = "lop_used", nullable = false, precision = 7, scale = 2)
    private BigDecimal lopUsed;

    @Version
    private Long version;

    @PrePersist
    void initializeAmounts() {
        if (openingBalance == null) openingBalance = BigDecimal.ZERO;
        if (annualEntitlement == null) annualEntitlement = BigDecimal.ZERO;
        if (carryForward == null) carryForward = BigDecimal.ZERO;
        if (accrued == null) accrued = BigDecimal.ZERO;
        if (paidUsed == null) paidUsed = BigDecimal.ZERO;
        if (lopUsed == null) lopUsed = BigDecimal.ZERO;
    }
}

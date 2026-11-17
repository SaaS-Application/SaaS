package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payrolls", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_employee_company_tenant_period",
                columnNames = {"employee_id", "company_id", "tenant_id", "pay_period"}),
        @UniqueConstraint(name = "uk_payroll_payslip_number", columnNames = "payslip_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "pay_period", nullable = false)
    private String payPeriod;

    @Column(name = "total_working_days", precision = 7, scale = 2)
    private BigDecimal totalWorkingDays;

    @Column(name = "paid_days", precision = 7, scale = 2)
    private BigDecimal paidDays;

    @Column(name = "unpaid_days", precision = 7, scale = 2)
    private BigDecimal unpaidDays;

    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal deductions;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_on")
    private LocalDate processedOn;

    @Column(name = "component_breakdown_json", columnDefinition = "text")
    private String componentBreakdownJson;

    @Column(name = "payslip_number", length = 42)
    private String payslipNumber;

    @Column(name = "company_name_snapshot")
    private String companyNameSnapshot;

    @Column(name = "company_address_snapshot")
    private String companyAddressSnapshot;

    @Column(name = "employee_name_snapshot", length = 511)
    private String employeeNameSnapshot;

    @Column(name = "employee_number_snapshot")
    private String employeeNumberSnapshot;

    @Column(name = "department_name_snapshot")
    private String departmentNameSnapshot;

    @Column(name = "designation_name_snapshot")
    private String designationNameSnapshot;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

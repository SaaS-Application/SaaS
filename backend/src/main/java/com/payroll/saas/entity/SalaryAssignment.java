package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_assignment_id_company_tenant", columnNames = {"id", "company_id", "tenant_id"})
}, indexes = {
        @Index(name = "idx_salary_assignment_employee_period",
                columnList = "employee_id,company_id,tenant_id,active,effective_from,effective_to"),
        @Index(name = "idx_salary_assignment_company_period",
                columnList = "company_id,tenant_id,active,effective_from")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "salary_structure_id", nullable = false)
    private Long salaryStructureId;

    @Column(name = "structure_code_snapshot", nullable = false, length = 32)
    private String structureCodeSnapshot;

    @Column(name = "structure_name_snapshot", nullable = false, length = 120)
    private String structureNameSnapshot;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "gross_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "illustrative_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal illustrativeNet;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

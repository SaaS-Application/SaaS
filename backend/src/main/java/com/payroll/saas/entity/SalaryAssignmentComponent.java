package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "salary_assignment_components", uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_assignment_component",
                columnNames = {"salary_assignment_id", "component_id"})
}, indexes = {
        @Index(name = "idx_salary_assignment_component_scope",
                columnList = "salary_assignment_id,company_id,tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAssignmentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "salary_assignment_id", nullable = false)
    private Long salaryAssignmentId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 16)
    private SalaryComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 20)
    private SalaryCalculationMethod calculationMethod;

    @Column(name = "component_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal value;

    @Column(name = "percentage_base_code", length = 32)
    private String percentageBaseCode;

    @Column(nullable = false)
    private boolean taxable;

    @Column(name = "calculated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal calculatedAmount;
}

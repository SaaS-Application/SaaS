package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "salary_structure_components", uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_structure_component",
                columnNames = {"salary_structure_id", "component_id"}),
        @UniqueConstraint(name = "uk_salary_structure_component_id_company_tenant",
                columnNames = {"id", "company_id", "tenant_id"})
}, indexes = {
        @Index(name = "idx_salary_structure_component_scope",
                columnList = "salary_structure_id,company_id,tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructureComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "salary_structure_id", nullable = false)
    private Long salaryStructureId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "percentage_base_component_id")
    private Long percentageBaseComponentId;

    @Column(name = "component_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal value;
}

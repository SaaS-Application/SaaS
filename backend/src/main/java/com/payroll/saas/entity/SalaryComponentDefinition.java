package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_component_definitions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_component_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_salary_component_id_company_tenant", columnNames = {"id", "company_id", "tenant_id"})
}, indexes = {
        @Index(name = "idx_salary_component_company_active", columnList = "company_id,tenant_id,active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryComponentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SalaryComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 20)
    private SalaryCalculationMethod calculationMethod;

    @Column(nullable = false)
    private boolean taxable;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

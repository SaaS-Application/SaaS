package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments", uniqueConstraints =
        @UniqueConstraint(name = "uk_department_company_name", columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}

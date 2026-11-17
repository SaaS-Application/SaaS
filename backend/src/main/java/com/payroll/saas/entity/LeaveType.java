package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_types", uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_type_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_leave_type_id_company_tenant", columnNames = {"id", "company_id", "tenant_id"})
}, indexes = {
        @Index(name = "idx_leave_type_company", columnList = "company_id,tenant_id,active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 24)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "annual_entitlement", nullable = false, precision = 7, scale = 2)
    private BigDecimal annualEntitlement;

    @Column(name = "carry_forward_limit", nullable = false, precision = 7, scale = 2)
    private BigDecimal carryForwardLimit;

    @Column(name = "max_consecutive_days", nullable = false)
    private int maxConsecutiveDays;

    @Column(name = "supporting_document_required", nullable = false)
    private boolean supportingDocumentRequired;

    @Column(name = "allow_past_applications", nullable = false)
    private boolean allowPastApplications;

    @Column(name = "allow_half_day", nullable = false)
    private boolean allowHalfDay;

    @Column(name = "allow_negative_balance", nullable = false)
    private boolean allowNegativeBalance;

    @Column(nullable = false)
    private boolean active;

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

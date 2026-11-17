package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balance_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_balance_request_action",
                columnNames = {"leave_request_id", "action", "leave_balance_id"})
}, indexes = {
        @Index(name = "idx_leave_ledger_balance", columnList = "leave_balance_id,created_at"),
        @Index(name = "idx_leave_ledger_balance_scope",
                columnList = "leave_balance_id,employee_id,company_id,tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
            @JoinColumn(name = "leave_balance_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", nullable = false),
            @JoinColumn(name = "company_id", referencedColumnName = "company_id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false)
    }, foreignKey = @ForeignKey(name = "fk_leave_ledger_balance_scope"))
    private LeaveBalance leaveBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "leave_request_id", referencedColumnName = "id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "employee_id", referencedColumnName = "employee_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "company_id", referencedColumnName = "company_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id",
                    insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_leave_ledger_request_scope"))
    private LeaveRequest leaveRequest;

    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", insertable = false, updatable = false)
    private User actor;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeaveLedgerAction action;

    @Column(name = "paid_delta", nullable = false, precision = 7, scale = 2)
    private BigDecimal paidDelta;

    @Column(name = "lop_delta", nullable = false, precision = 7, scale = 2)
    private BigDecimal lopDelta;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    void initialize() {
        if (paidDelta == null) paidDelta = BigDecimal.ZERO;
        if (lopDelta == null) lopDelta = BigDecimal.ZERO;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (leaveRequest != null) leaveRequestId = leaveRequest.getId();
        if (actor != null) actorUserId = actor.getId();
    }
}

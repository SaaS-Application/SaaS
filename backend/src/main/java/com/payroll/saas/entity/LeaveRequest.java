package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leave_request_company_status", columnList = "company_id,tenant_id,status,start_date"),
        @Index(name = "idx_leave_request_employee_dates", columnList = "employee_id,start_date,end_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_request_id_employee_company_tenant",
                columnNames = {"id", "employee_id", "company_id", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id")
    private Long leaveTypeId;

    @Column(name = "leave_type", nullable = false, length = 120)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal days;

    @Column(name = "working_dates", columnDefinition = "text")
    private String workingDates;

    @Column(name = "leave_paid", nullable = false)
    private boolean paid;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_period", length = 16)
    private LeaveHalfDayPeriod halfDayPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeaveRequestStatus status;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "cancellation_comment", length = 1000)
    private String cancellationComment;

    @Column(name = "supporting_document_key", length = 36)
    private String supportingDocumentKey;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Version
    private Long version;

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

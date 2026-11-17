package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_employee_date", columnNames = {"employee_id", "date"})
}, indexes = {
        @Index(name = "idx_attendance_employee_scope", columnList = "employee_id,company_id,tenant_id"),
        @Index(name = "idx_attendance_company_date", columnList = "company_id,tenant_id,date"),
        @Index(name = "idx_attendance_leave_request_scope",
                columnList = "leave_request_id,employee_id,company_id,tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AttendanceStatus status;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_in_instant")
    private Instant checkInInstant;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "check_out_instant")
    private Instant checkOutInstant;

    @Column(name = "time_zone_id", nullable = false, length = 64,
            columnDefinition = "varchar(64) default 'UTC'")
    private String timeZoneId;

    @Builder.Default
    @Column(name = "total_working_minutes", nullable = false, columnDefinition = "integer default 0")
    private int totalWorkingMinutes = 0;

    @Builder.Default
    @Column(name = "late_arrival", nullable = false, columnDefinition = "boolean default false")
    private boolean lateArrival = false;

    @Builder.Default
    @Column(name = "early_departure", nullable = false, columnDefinition = "boolean default false")
    private boolean earlyDeparture = false;

    @Builder.Default
    @Column(name = "overtime_minutes", nullable = false, columnDefinition = "integer default 0")
    private int overtimeMinutes = 0;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 24,
            columnDefinition = "varchar(24) default 'NOT_REQUIRED'")
    private AttendanceApprovalStatus approvalStatus;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "leave_request_id")
    private Long leaveRequestId;

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
    }, foreignKey = @ForeignKey(name = "fk_attendance_leave_request_scope"))
    private LeaveRequest leaveRequest;

    @Column(name = "leave_day_fraction", precision = 3, scale = 2)
    private BigDecimal leaveDayFraction;

    @Column(name = "leave_paid")
    private Boolean leavePaid;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (approvalStatus == null) {
            approvalStatus = AttendanceApprovalStatus.NOT_REQUIRED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

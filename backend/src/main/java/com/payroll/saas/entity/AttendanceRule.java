package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "attendance_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_rules_company", columnNames = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime;

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime;

    @Column(name = "required_working_minutes", nullable = false)
    private int requiredWorkingMinutes;

    @Column(name = "grace_period_minutes", nullable = false)
    private int gracePeriodMinutes;

    @Column(name = "half_day_threshold_minutes", nullable = false)
    private int halfDayThresholdMinutes;

    @Column(name = "overtime_threshold_minutes", nullable = false)
    private int overtimeThresholdMinutes;

    @Column(name = "weekly_holidays", nullable = false, length = 80)
    private String weeklyHolidays;

    @Column(name = "time_zone_id", nullable = false, length = 64)
    private String timeZoneId;
}

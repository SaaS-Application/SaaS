package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "company_holidays", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_holiday_date", columnNames = {"company_id", "date"})
}, indexes = {
        @Index(name = "idx_company_holiday_range", columnList = "company_id,tenant_id,date,active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;
}

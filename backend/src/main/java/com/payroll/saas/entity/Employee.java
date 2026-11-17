package com.payroll.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_company_number", columnNames = {"company_id", "employee_number"}),
        @UniqueConstraint(name = "uk_employee_company_email", columnNames = {"company_id", "email"}),
        @UniqueConstraint(name = "uk_employee_user", columnNames = {"user_id"}),
        @UniqueConstraint(name = "uk_employee_id_company_tenant", columnNames = {"id", "company_id", "tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(name = "employee_number", nullable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 32)
    private String gender;

    @Column(name = "job_title", nullable = false, length = 120)
    private String jobTitle;

    @Column(name = "reporting_manager_id")
    private Long reportingManagerId;

    @Column(name = "emergency_contact_name", length = 120)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 40)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 80)
    private String emergencyContactRelationship;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_employee_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "employment_status", nullable = false)
    private String employmentStatus;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDate.now();
        }
    }
}

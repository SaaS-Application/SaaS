package com.payroll.saas.repository;

import com.payroll.saas.entity.SalaryAssignmentComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryAssignmentComponentRepository extends JpaRepository<SalaryAssignmentComponent, Long> {
    List<SalaryAssignmentComponent> findBySalaryAssignmentIdAndCompanyIdAndTenantIdOrderById(
            Long salaryAssignmentId, Long companyId, Long tenantId);
}

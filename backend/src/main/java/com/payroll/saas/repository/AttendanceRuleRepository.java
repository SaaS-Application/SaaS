package com.payroll.saas.repository;

import com.payroll.saas.entity.AttendanceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceRuleRepository extends JpaRepository<AttendanceRule, Long> {
    Optional<AttendanceRule> findByCompanyIdAndTenantId(Long companyId, Long tenantId);
}

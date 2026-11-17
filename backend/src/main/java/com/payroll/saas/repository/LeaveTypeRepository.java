package com.payroll.saas.repository;

import com.payroll.saas.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    List<LeaveType> findByCompanyIdAndTenantIdOrderByName(Long companyId, Long tenantId);
    List<LeaveType> findByCompanyIdAndTenantIdAndActiveTrueOrderByName(Long companyId, Long tenantId);
    Optional<LeaveType> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
    boolean existsByCompanyIdAndCodeIgnoreCaseAndIdNot(Long companyId, String code, Long id);
    long countByCompanyIdAndTenantIdAndActiveTrue(Long companyId, Long tenantId);
}

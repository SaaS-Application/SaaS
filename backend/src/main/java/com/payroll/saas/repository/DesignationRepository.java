package com.payroll.saas.repository;

import com.payroll.saas.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findByCompanyIdAndTenantId(Long companyId, Long tenantId);
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);
    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}

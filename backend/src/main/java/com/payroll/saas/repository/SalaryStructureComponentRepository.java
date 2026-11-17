package com.payroll.saas.repository;

import com.payroll.saas.entity.SalaryStructureComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryStructureComponentRepository extends JpaRepository<SalaryStructureComponent, Long> {
    List<SalaryStructureComponent> findBySalaryStructureIdAndCompanyIdAndTenantIdOrderById(
            Long salaryStructureId, Long companyId, Long tenantId);
    void deleteBySalaryStructureIdAndCompanyIdAndTenantId(
            Long salaryStructureId, Long companyId, Long tenantId);
    boolean existsByComponentIdAndCompanyIdAndTenantId(Long componentId, Long companyId, Long tenantId);
}

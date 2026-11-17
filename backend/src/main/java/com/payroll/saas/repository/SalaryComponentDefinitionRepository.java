package com.payroll.saas.repository;

import com.payroll.saas.entity.SalaryComponentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryComponentDefinitionRepository extends JpaRepository<SalaryComponentDefinition, Long> {
    List<SalaryComponentDefinition> findByCompanyIdAndTenantIdOrderByName(Long companyId, Long tenantId);
    List<SalaryComponentDefinition> findByCompanyIdAndTenantIdAndActiveTrueOrderByName(Long companyId, Long tenantId);
    Optional<SalaryComponentDefinition> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    boolean existsByCompanyIdAndTenantIdAndCodeIgnoreCase(Long companyId, Long tenantId, String code);
    boolean existsByCompanyIdAndTenantIdAndCodeIgnoreCaseAndIdNot(
            Long companyId, Long tenantId, String code, Long id);
}

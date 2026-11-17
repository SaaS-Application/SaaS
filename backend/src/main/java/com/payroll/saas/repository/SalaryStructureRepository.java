package com.payroll.saas.repository;

import com.payroll.saas.entity.SalaryStructure;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {
    List<SalaryStructure> findByCompanyIdAndTenantIdOrderByName(Long companyId, Long tenantId);
    Optional<SalaryStructure> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    boolean existsByCompanyIdAndTenantIdAndCodeIgnoreCase(Long companyId, Long tenantId, String code);
    boolean existsByCompanyIdAndTenantIdAndCodeIgnoreCaseAndIdNot(
            Long companyId, Long tenantId, String code, Long id);
    long countByCompanyIdAndTenantIdAndActiveTrue(Long companyId, Long tenantId);

    @Query("""
            select count(s) from SalaryStructure s
            where s.companyId = :companyId and s.tenantId = :tenantId and s.active = true
              and s.effectiveFrom <= :date and (s.effectiveTo is null or s.effectiveTo >= :date)
            """)
    long countEffectiveByCompanyAndTenant(
            @Param("companyId") Long companyId, @Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SalaryStructure s
            where s.id = :id and s.companyId = :companyId and s.tenantId = :tenantId
            """)
    Optional<SalaryStructure> findForUpdate(
            @Param("id") Long id, @Param("companyId") Long companyId, @Param("tenantId") Long tenantId);
}

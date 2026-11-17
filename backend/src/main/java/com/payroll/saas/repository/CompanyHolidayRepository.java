package com.payroll.saas.repository;

import com.payroll.saas.entity.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    List<CompanyHoliday> findByCompanyIdAndTenantIdOrderByDateAsc(Long companyId, Long tenantId);
    List<CompanyHoliday> findByCompanyIdAndTenantIdAndDateBetweenAndActiveTrue(
            Long companyId, Long tenantId, LocalDate from, LocalDate to);
    Optional<CompanyHoliday> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    boolean existsByCompanyIdAndDateAndIdNot(Long companyId, LocalDate date, Long id);
    boolean existsByCompanyIdAndDate(Long companyId, LocalDate date);
    long countByCompanyIdAndTenantIdAndActiveTrue(Long companyId, Long tenantId);
}

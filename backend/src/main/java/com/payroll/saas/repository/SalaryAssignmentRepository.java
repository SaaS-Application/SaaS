package com.payroll.saas.repository;

import com.payroll.saas.entity.SalaryAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalaryAssignmentRepository extends JpaRepository<SalaryAssignment, Long> {
    List<SalaryAssignment> findByEmployeeIdAndCompanyIdAndTenantIdOrderByEffectiveFromDescIdDesc(
            Long employeeId, Long companyId, Long tenantId);
    Optional<SalaryAssignment> findByIdAndEmployeeIdAndCompanyIdAndTenantId(
            Long id, Long employeeId, Long companyId, Long tenantId);
    long countByCompanyIdAndTenantIdAndActiveTrue(Long companyId, Long tenantId);

    @Query("""
            select count(a) from SalaryAssignment a
            where a.companyId = :companyId and a.tenantId = :tenantId and a.active = true
              and a.effectiveFrom <= :date and (a.effectiveTo is null or a.effectiveTo >= :date)
            """)
    long countEffectiveByCompanyAndTenant(
            @Param("companyId") Long companyId, @Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Query("""
            select a from SalaryAssignment a
            where a.employeeId = :employeeId and a.companyId = :companyId and a.tenantId = :tenantId
              and a.active = true
              and (:effectiveTo is null or a.effectiveFrom <= :effectiveTo)
              and (a.effectiveTo is null or a.effectiveTo >= :effectiveFrom)
            order by a.effectiveFrom
            """)
    List<SalaryAssignment> findOverlapping(
            @Param("employeeId") Long employeeId,
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo);

    @Query("""
            select a from SalaryAssignment a
            where a.employeeId = :employeeId and a.companyId = :companyId and a.tenantId = :tenantId
              and a.active = true and a.effectiveFrom <= :date
              and (a.effectiveTo is null or a.effectiveTo >= :date)
            """)
    Optional<SalaryAssignment> findCurrent(
            @Param("employeeId") Long employeeId,
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date);
}

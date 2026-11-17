package com.payroll.saas.repository;

import com.payroll.saas.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByTenantId(Long tenantId);
    List<Payroll> findByCompanyIdAndTenantId(Long companyId, Long tenantId);
    List<Payroll> findByCompanyIdAndTenantIdOrderByPayPeriodDescEmployeeIdAsc(Long companyId, Long tenantId);
    List<Payroll> findByCompanyIdAndTenantIdAndPayPeriodOrderByEmployeeIdAsc(
            Long companyId, Long tenantId, String payPeriod);
    List<Payroll> findByEmployeeIdAndCompanyIdAndTenantIdOrderByPayPeriodDesc(
            Long employeeId, Long companyId, Long tenantId);
    Optional<Payroll> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    Optional<Payroll> findByIdAndEmployeeIdAndCompanyIdAndTenantId(
            Long id, Long employeeId, Long companyId, Long tenantId);
    boolean existsByEmployeeIdAndCompanyIdAndTenantIdAndPayPeriod(
            Long employeeId, Long companyId, Long tenantId, String payPeriod);
    @Query("""
            select p from Payroll p
            where p.employeeId = :employeeId and p.companyId = :companyId and p.tenantId = :tenantId
              and p.status in :statuses
              and (:payPeriod is null or p.payPeriod = :payPeriod)
            order by p.payPeriod desc
            """)
    List<Payroll> findPayslipsForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("payPeriod") String payPeriod,
            @Param("statuses") List<String> statuses);

    @Query("""
            select p from Payroll p join Employee e on e.id = p.employeeId
            where p.companyId = :companyId and p.tenantId = :tenantId
              and p.status in :statuses
              and (:payPeriod is null or p.payPeriod = :payPeriod)
              and (:search is null
                   or lower(coalesce(p.employeeNameSnapshot, concat(e.firstName, ' ', e.lastName)))
                      like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.employeeNumberSnapshot, e.employeeNumber))
                      like lower(concat('%', :search, '%')))
            order by p.payPeriod desc, p.employeeId asc
            """)
    List<Payroll> findPayslipsForCompany(
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("payPeriod") String payPeriod,
            @Param("search") String search,
            @Param("statuses") List<String> statuses);

    @Query("""
            select p from Payroll p
            where (:companyId is null or p.companyId = :companyId)
              and (:tenantId is null or p.tenantId = :tenantId)
              and (:payPeriod is null or p.payPeriod = :payPeriod)
            order by p.payPeriod desc, p.companyId asc, p.employeeId asc
            """)
    List<Payroll> searchPayrolls(
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("payPeriod") String payPeriod);
}

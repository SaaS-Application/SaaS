package com.payroll.saas.repository;

import com.payroll.saas.entity.Attendance;
import com.payroll.saas.entity.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByEmployeeIdAndDate(Long employeeId, LocalDate date);

    Optional<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);

    List<Attendance> findByLeaveRequestIdOrderByDateAsc(Long leaveRequestId);

    void deleteByLeaveRequestId(Long leaveRequestId);

    Optional<Attendance> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);

    List<Attendance> findByEmployeeIdAndCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
            Long employeeId, Long companyId, Long tenantId, LocalDate from, LocalDate to);

    List<Attendance> findByCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
            Long companyId, Long tenantId, LocalDate from, LocalDate to);

    List<Attendance> findByCompanyIdAndTenantIdAndDateOrderByEmployeeIdAsc(
            Long companyId, Long tenantId, LocalDate date);

    @Query("""
            select a from Attendance a
            where a.companyId = :companyId and a.tenantId = :tenantId and a.date = :date
              and (:status is null or a.status = :status)
              and exists (
                  select e.id from Employee e
                  where e.id = a.employeeId and e.companyId = a.companyId and e.tenantId = a.tenantId
                    and (:departmentId is null or e.department.id = :departmentId)
                    and (:search is null
                         or lower(e.firstName) like lower(concat('%', :search, '%'))
                         or lower(e.lastName) like lower(concat('%', :search, '%'))
                         or lower(concat(e.firstName, ' ', e.lastName)) like lower(concat('%', :search, '%'))
                         or lower(e.employeeNumber) like lower(concat('%', :search, '%')))
              )
            """)
    Page<Attendance> searchCompanyDay(
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("departmentId") Long departmentId,
            @Param("search") String search,
            @Param("status") AttendanceStatus status,
            Pageable pageable);
}

package com.payroll.saas.repository;

import com.payroll.saas.entity.LeaveRequest;
import com.payroll.saas.entity.LeaveRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdAndCompanyIdAndTenantIdOrderByCreatedAtDesc(
            Long employeeId, Long companyId, Long tenantId);

    Page<LeaveRequest> findByEmployeeIdAndCompanyIdAndTenantIdOrderByCreatedAtDesc(
            Long employeeId, Long companyId, Long tenantId, Pageable pageable);

    List<LeaveRequest> findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveTypeIdAndStatus(
            Long employeeId, Long companyId, Long tenantId, Long leaveTypeId, LeaveRequestStatus status);

    Optional<LeaveRequest> findByIdAndEmployeeIdAndCompanyIdAndTenantId(
            Long id, Long employeeId, Long companyId, Long tenantId);

    Optional<LeaveRequest> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from LeaveRequest r
            where r.id = :id and r.companyId = :companyId and r.tenantId = :tenantId
            """)
    Optional<LeaveRequest> findForUpdate(
            @Param("id") Long id, @Param("companyId") Long companyId, @Param("tenantId") Long tenantId);

    boolean existsByEmployeeIdAndCompanyIdAndTenantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, Long companyId, Long tenantId, Collection<LeaveRequestStatus> statuses,
            LocalDate endDate, LocalDate startDate);

    List<LeaveRequest> findByCompanyIdAndTenantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long companyId, Long tenantId, Collection<LeaveRequestStatus> statuses,
            LocalDate endDate, LocalDate startDate);

    long countByCompanyIdAndTenantIdAndStatus(Long companyId, Long tenantId, LeaveRequestStatus status);

    @Query("""
            select r from LeaveRequest r
            where r.companyId = :companyId and r.tenantId = :tenantId
              and (:status is null or r.status = :status)
              and (:employeeId is null or r.employeeId = :employeeId)
              and (:leaveTypeId is null or r.leaveTypeId = :leaveTypeId)
              and (:fromDate is null or r.endDate >= :fromDate)
              and (:toDate is null or r.startDate <= :toDate)
              and (:departmentId is null or exists (
                  select e.id from Employee e where e.id = r.employeeId and e.department.id = :departmentId
              ))
              and (:designationId is null or exists (
                  select e.id from Employee e where e.id = r.employeeId and e.designation.id = :designationId
              ))
              and (:search is null or exists (
                  select e.id from Employee e where e.id = r.employeeId
                    and (lower(e.firstName) like lower(concat('%', :search, '%'))
                      or lower(e.lastName) like lower(concat('%', :search, '%'))
                      or lower(e.employeeNumber) like lower(concat('%', :search, '%')))
              ))
            """)
    Page<LeaveRequest> searchCompanyRequests(
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("status") LeaveRequestStatus status,
            @Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("departmentId") Long departmentId,
            @Param("designationId") Long designationId,
            @Param("search") String search,
            Pageable pageable);
}

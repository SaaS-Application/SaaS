package com.payroll.saas.repository;

import com.payroll.saas.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveYearOrderByLeaveTypeId(
            Long employeeId, Long companyId, Long tenantId, int leaveYear);
    Optional<LeaveBalance> findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveTypeIdAndLeaveYear(
            Long employeeId, Long companyId, Long tenantId, Long leaveTypeId, int leaveYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from LeaveBalance b
            where b.employeeId = :employeeId and b.companyId = :companyId
              and b.tenantId = :tenantId and b.leaveTypeId = :leaveTypeId
              and b.leaveYear = :leaveYear
            """)
    Optional<LeaveBalance> findForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("leaveYear") int leaveYear);
}

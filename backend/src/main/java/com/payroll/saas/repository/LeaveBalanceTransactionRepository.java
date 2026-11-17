package com.payroll.saas.repository;

import com.payroll.saas.entity.LeaveBalanceTransaction;
import com.payroll.saas.entity.LeaveLedgerAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceTransactionRepository extends JpaRepository<LeaveBalanceTransaction, Long> {
    List<LeaveBalanceTransaction> findByLeaveBalance_IdOrderByCreatedAtAsc(Long leaveBalanceId);
    Optional<LeaveBalanceTransaction> findByLeaveRequest_IdAndAction(Long leaveRequestId, LeaveLedgerAction action);
    Optional<LeaveBalanceTransaction> findByLeaveRequest_IdAndActionAndLeaveBalance_Id(
            Long leaveRequestId, LeaveLedgerAction action, Long leaveBalanceId);
}

package com.payroll.saas.service;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.*;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.*;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveManagementService {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.50");
    private static final BigDecimal FULL_DAY = BigDecimal.ONE;
    private static final List<LeaveRequestStatus> OVERLAPPING_STATUSES = List.of(
            LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCELLATION_PENDING);

    private final LeaveTypeRepository leaveTypeRepository;
    private final CompanyHolidayRepository holidayRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceTransactionRepository transactionRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceRuleRepository attendanceRuleRepository;
    private final TenantSecurityService tenantSecurityService;
    private final EmployeeIdentityService employeeIdentityService;
    private final AuditLogService auditLogService;
    private final LeaveDocumentStorageService documentStorage;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> employeeLeaveTypes(User actor) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        return leaveTypeRepository.findByCompanyIdAndTenantIdAndActiveTrueOrderByName(
                        company.getId(), company.getTenantId()).stream()
                .map(LeaveTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> companyLeaveTypes(User actor) {
        Company company = managedCompany(actor);
        return leaveTypeRepository.findByCompanyIdAndTenantIdOrderByName(company.getId(), company.getTenantId())
                .stream().map(LeaveTypeResponse::from).toList();
    }

    @Transactional
    public LeaveTypeResponse createLeaveType(User actor, LeaveTypeRequest request) {
        Company company = managedCompany(actor);
        String code = normalizeCode(request.getCode());
        if (leaveTypeRepository.existsByCompanyIdAndCodeIgnoreCase(company.getId(), code)) {
            throw conflict("Leave type code already exists in this company");
        }
        LeaveType type = new LeaveType();
        type.setCompanyId(company.getId());
        type.setTenantId(company.getTenantId());
        applyType(type, request, code);
        type.setActive(true);
        LeaveType saved = leaveTypeRepository.save(type);
        audit(actor, company, "LEAVE_TYPE_CREATED", "Created leave type " + saved.getCode());
        return LeaveTypeResponse.from(saved);
    }

    @Transactional
    public LeaveTypeResponse updateLeaveType(User actor, Long typeId, LeaveTypeRequest request) {
        Company company = managedCompany(actor);
        LeaveType type = findLeaveType(typeId, company);
        String code = normalizeCode(request.getCode());
        if (leaveTypeRepository.existsByCompanyIdAndCodeIgnoreCaseAndIdNot(company.getId(), code, typeId)) {
            throw conflict("Leave type code already exists in this company");
        }
        applyType(type, request, code);
        LeaveType saved = leaveTypeRepository.save(type);
        audit(actor, company, "LEAVE_TYPE_UPDATED", "Updated leave type " + saved.getCode());
        return LeaveTypeResponse.from(saved);
    }

    @Transactional
    public LeaveTypeResponse setLeaveTypeActive(User actor, Long typeId, boolean active) {
        Company company = managedCompany(actor);
        LeaveType type = findLeaveType(typeId, company);
        type.setActive(active);
        LeaveType saved = leaveTypeRepository.save(type);
        audit(actor, company, active ? "LEAVE_TYPE_ACTIVATED" : "LEAVE_TYPE_DEACTIVATED",
                "Changed leave type " + saved.getCode() + " active status to " + active);
        return LeaveTypeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CompanyHolidayResponse> companyHolidays(User actor) {
        Company company = managedCompany(actor);
        return holidayRepository.findByCompanyIdAndTenantIdOrderByDateAsc(company.getId(), company.getTenantId())
                .stream().map(CompanyHolidayResponse::from).toList();
    }

    @Transactional
    public CompanyHolidayResponse createHoliday(User actor, CompanyHolidayRequest request) {
        Company company = managedCompany(actor);
        if (holidayRepository.existsByCompanyIdAndDate(company.getId(), request.getDate())) {
            throw conflict("A company holiday is already configured for this date");
        }
        CompanyHoliday holiday = CompanyHoliday.builder().companyId(company.getId()).tenantId(company.getTenantId())
                .name(request.getName().trim()).date(request.getDate()).description(clean(request.getDescription()))
                .active(true).build();
        CompanyHoliday saved = holidayRepository.save(holiday);
        audit(actor, company, "COMPANY_HOLIDAY_CREATED", "Created holiday " + saved.getName() + " on " + saved.getDate());
        return CompanyHolidayResponse.from(saved);
    }

    @Transactional
    public CompanyHolidayResponse updateHoliday(User actor, Long holidayId, CompanyHolidayRequest request) {
        Company company = managedCompany(actor);
        CompanyHoliday holiday = findHoliday(holidayId, company);
        if (holidayRepository.existsByCompanyIdAndDateAndIdNot(company.getId(), request.getDate(), holidayId)) {
            throw conflict("A company holiday is already configured for this date");
        }
        holiday.setName(request.getName().trim());
        holiday.setDate(request.getDate());
        holiday.setDescription(clean(request.getDescription()));
        CompanyHoliday saved = holidayRepository.save(holiday);
        audit(actor, company, "COMPANY_HOLIDAY_UPDATED", "Updated holiday " + saved.getName());
        return CompanyHolidayResponse.from(saved);
    }

    @Transactional
    public CompanyHolidayResponse setHolidayActive(User actor, Long holidayId, boolean active) {
        Company company = managedCompany(actor);
        CompanyHoliday holiday = findHoliday(holidayId, company);
        holiday.setActive(active);
        CompanyHoliday saved = holidayRepository.save(holiday);
        audit(actor, company, active ? "COMPANY_HOLIDAY_ACTIVATED" : "COMPANY_HOLIDAY_DEACTIVATED",
                "Changed holiday " + saved.getName() + " active status to " + active);
        return CompanyHolidayResponse.from(saved);
    }

    @Transactional
    public LeaveApplicationResponse apply(User actor, LeaveApplicationRequest input, MultipartFile document) {
        Company company = companyForEmployee(actor);
        Employee linkedEmployee = currentEmployee(actor);
        Employee employee = employeeRepository.findForUpdate(
                        linkedEmployee.getId(), company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Inactive employees cannot submit leave applications");
        }
        LeaveType type = findLeaveType(input.getLeaveTypeId(), company);
        if (!type.isActive()) throw new ApiException("This leave type is inactive");
        validateDateRange(input.getStartDate(), input.getEndDate());
        LocalDate today = today(company);
        if (!type.isAllowPastApplications() && input.getStartDate().isBefore(today)) {
            throw new ApiException("Past leave dates are not allowed by this company policy");
        }

        Set<LocalDate> workingDates = workingDayDates(company, input.getStartDate(), input.getEndDate());
        BigDecimal days = calculateApplicationDays(type, input, workingDates);
        if (days.compareTo(BigDecimal.valueOf(type.getMaxConsecutiveDays())) > 0) {
            throw new ApiException("The requested leave exceeds the configured maximum consecutive days");
        }
        if (type.isSupportingDocumentRequired() && (document == null || document.isEmpty())) {
            throw new ApiException("A supporting document is required for this leave type");
        }
        if (requestRepository.existsByEmployeeIdAndCompanyIdAndTenantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employee.getId(), company.getId(), company.getTenantId(), OVERLAPPING_STATUSES,
                input.getEndDate(), input.getStartDate())) {
            throw conflict("This application overlaps another pending or approved leave request");
        }

        LeaveRequest request = LeaveRequest.builder()
                .tenantId(company.getTenantId()).companyId(company.getId()).employeeId(employee.getId())
                .leaveTypeId(type.getId()).leaveType(type.getName())
                .startDate(input.getStartDate()).endDate(input.getEndDate()).days(days)
                .workingDates(workingDates.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .paid(type.isPaid())
                .halfDay(input.isHalfDay()).halfDayPeriod(input.getHalfDayPeriod())
                .status(LeaveRequestStatus.PENDING).reason(input.getReason().trim()).build();

        LeaveRequest saved = requestRepository.saveAndFlush(request);
        String documentKey = null;
        try {
            if (document != null && !document.isEmpty()) {
                documentKey = documentStorage.store(document);
                saved.setSupportingDocumentKey(documentKey);
                saved = requestRepository.saveAndFlush(saved);
            }
        } catch (IOException exception) {
            cleanupDocument(documentKey, exception);
            throw new ApiException("The supporting document could not be stored");
        } catch (RuntimeException exception) {
            cleanupDocument(documentKey, exception);
            throw exception;
        }

        audit(actor, company, "LEAVE_APPLIED", "Employee " + employee.getId() + " applied for " + days + " day(s)");
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplicationResponse> employeeApplications(User actor, int page, int size) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        validatePage(page, size);
        return requestRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByCreatedAtDesc(
                        employee.getId(), company.getId(), company.getTenantId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse employeeApplication(User actor, Long requestId) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        LeaveRequest request = requestRepository.findByIdAndEmployeeIdAndCompanyIdAndTenantId(
                        requestId, employee.getId(), company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found"));
        return response(request);
    }

    @Transactional
    public LeaveApplicationResponse withdraw(User actor, Long requestId) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        LeaveRequest request = requestRepository.findForUpdate(requestId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found"));
        assertRequestOwner(request, employee);
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw conflict("Only pending leave applications can be withdrawn");
        }
        request.setStatus(LeaveRequestStatus.WITHDRAWN);
        LeaveRequest saved = requestRepository.save(request);
        audit(actor, company, "LEAVE_WITHDRAWN", "Withdrew leave application " + requestId);
        return response(saved);
    }

    @Transactional
    public LeaveApplicationResponse requestCancellation(User actor, Long requestId, String comment) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        LeaveRequest request = requestRepository.findForUpdate(requestId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found"));
        assertRequestOwner(request, employee);
        if (request.getStatus() != LeaveRequestStatus.APPROVED || !request.getStartDate().isAfter(today(company))) {
            throw conflict("Only future approved leave can be cancelled");
        }
        request.setStatus(LeaveRequestStatus.CANCELLATION_PENDING);
        request.setCancellationComment(clean(comment));
        LeaveRequest saved = requestRepository.save(request);
        audit(actor, company, "LEAVE_CANCELLATION_REQUESTED", "Requested cancellation for leave " + requestId);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplicationResponse> companyApplications(
            User actor, LeaveRequestStatus status, Long employeeId, Long leaveTypeId,
            LocalDate fromDate, LocalDate toDate, Long departmentId, Long designationId,
            String search, int page, int size) {
        Company company = managedCompany(actor);
        validatePage(page, size);
        validateOptionalDateRange(fromDate, toDate);
        if (employeeId != null) findEmployee(employeeId, company);
        if (leaveTypeId != null) findLeaveType(leaveTypeId, company);
        return requestRepository.searchCompanyRequests(company.getId(), company.getTenantId(), status,
                        employeeId, leaveTypeId, fromDate, toDate, departmentId, designationId,
                        emptyToNull(search), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse companyApplication(User actor, Long requestId) {
        Company company = managedCompany(actor);
        return response(requestRepository.findByIdAndCompanyIdAndTenantId(requestId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found")));
    }

    @Transactional
    public LeaveApplicationResponse decide(User actor, Long requestId, LeaveDecisionRequest input) {
        Company company = managedCompany(actor);
        LeaveRequest request = requestRepository.findForUpdate(requestId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found"));
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw conflict("Only pending leave applications can be approved or rejected");
        }
        Employee employee = findEmployee(request.getEmployeeId(), company);
        if (employee.getUser() != null && Objects.equals(employee.getUser().getId(), actor.getId())) {
            throw new AccessDeniedException("Employees cannot approve their own leave");
        }
        String decision = input.getDecision().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(decision)) {
            applyBalanceDeduction(actor, company, employee, request);
            createLeaveAttendance(actor, company, employee, request);
            request.setStatus(LeaveRequestStatus.APPROVED);
        } else if ("REJECT".equals(decision)) {
            if (input.getComment() == null || input.getComment().isBlank()) {
                throw new ApiException("A rejection comment is required");
            }
            request.setStatus(LeaveRequestStatus.REJECTED);
        } else {
            throw new ApiException("Decision must be APPROVE or REJECT");
        }
        request.setReviewComment(clean(input.getComment()));
        request.setReviewedBy(actor.getId());
        request.setReviewedAt(LocalDateTime.now(clock));
        LeaveRequest saved = requestRepository.saveAndFlush(request);
        audit(actor, company, "LEAVE_" + decision, "Decision " + decision + " for leave application " + requestId);
        return response(saved);
    }

    @Transactional
    public LeaveApplicationResponse decideCancellation(
            User actor, Long requestId, LeaveCancellationDecisionRequest input) {
        Company company = managedCompany(actor);
        LeaveRequest request = requestRepository.findForUpdate(requestId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave application not found"));
        if (request.getStatus() != LeaveRequestStatus.CANCELLATION_PENDING) {
            throw conflict("This leave application has no pending cancellation request");
        }
        Employee employee = findEmployee(request.getEmployeeId(), company);
        if (Boolean.TRUE.equals(input.getApprove())) {
            if (!request.getStartDate().isAfter(today(company))) {
                throw conflict("Only future approved leave can be cancelled");
            }
            reverseBalanceDeduction(actor, company, request);
            reverseLeaveAttendance(request);
            request.setStatus(LeaveRequestStatus.CANCELLED);
        } else {
            request.setStatus(LeaveRequestStatus.APPROVED);
        }
        request.setReviewComment(clean(input.getComment()));
        request.setReviewedBy(actor.getId());
        request.setReviewedAt(LocalDateTime.now(clock));
        LeaveRequest saved = requestRepository.saveAndFlush(request);
        audit(actor, company, Boolean.TRUE.equals(input.getApprove())
                        ? "LEAVE_CANCELLATION_APPROVED" : "LEAVE_CANCELLATION_REJECTED",
                "Cancellation decision for leave application " + requestId + "; employee " + employee.getId());
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> employeeBalances(User actor, int year) {
        Employee employee = currentEmployee(actor);
        Company company = companyForEmployee(actor);
        return balancesFor(employee, company, year);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> companyEmployeeBalances(User actor, Long employeeId, int year) {
        Company company = managedCompany(actor);
        Employee employee = findEmployee(employeeId, company);
        return balancesFor(employee, company, year);
    }

    @Transactional
    public LeaveBalanceResponse allocateBalance(User actor, LeaveBalanceAllocationRequest input) {
        Company company = managedCompany(actor);
        Employee employee = findEmployee(input.getEmployeeId(), company);
        LeaveType type = findLeaveType(input.getLeaveTypeId(), company);
        BigDecimal carry = amount(input.getCarryForward());
        if (carry.compareTo(type.getCarryForwardLimit()) > 0) {
            throw new ApiException("Carry-forward allocation exceeds this leave type's configured limit");
        }
        Optional<LeaveBalance> found = balanceRepository.findForUpdate(employee.getId(), company.getId(),
                company.getTenantId(), type.getId(), input.getLeaveYear());
        LeaveBalance balance = found.orElseGet(() -> LeaveBalance.builder().employeeId(employee.getId())
                .companyId(company.getId()).tenantId(company.getTenantId()).leaveTypeId(type.getId())
                .leaveYear(input.getLeaveYear()).openingBalance(BigDecimal.ZERO).annualEntitlement(BigDecimal.ZERO)
                .carryForward(BigDecimal.ZERO).accrued(BigDecimal.ZERO).paidUsed(BigDecimal.ZERO)
                .lopUsed(BigDecimal.ZERO).build());
        BigDecimal oldAllocation = allocationTotal(balance);
        balance.setOpeningBalance(amount(input.getOpeningBalance()));
        balance.setAnnualEntitlement(input.getAnnualEntitlement() == null
                ? type.getAnnualEntitlement() : amount(input.getAnnualEntitlement()));
        balance.setCarryForward(carry);
        balance.setAccrued(amount(input.getAccrued()));
        BigDecimal newAllocation = allocationTotal(balance);
        if (type.isPaid() && !type.isAllowNegativeBalance()
                && newAllocation.subtract(balance.getPaidUsed()).signum() < 0) {
            throw conflict("Allocation cannot be reduced below leave already used");
        }
        balance = balanceRepository.saveAndFlush(balance);
        transactionRepository.save(ledgerTransaction(balance, null, actor, LeaveLedgerAction.ALLOCATION,
                newAllocation.subtract(oldAllocation), BigDecimal.ZERO, clean(input.getComment())));
        audit(actor, company, "LEAVE_BALANCE_ALLOCATED",
                "Updated balance for employee " + employee.getId() + " and leave type " + type.getCode());
        return balanceResponse(balance, type,
                pendingForYear(employee, company, type, input.getLeaveYear()));
    }

    @Transactional(readOnly = true)
    public LeaveOverviewResponse companyOverview(User actor) {
        Company company = managedCompany(actor);
        return overview(company);
    }

    @Transactional(readOnly = true)
    public LeaveOverviewResponse superAdminOverview(User actor, Long companyId) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
        return overview(tenantSecurityService.assertCompanyAccess(actor, companyId));
    }

    @Transactional(readOnly = true)
    public DocumentDownload document(User actor, Long requestId) {
        LeaveRequest request;
        if (actor != null && actor.getRole() == UserRole.EMPLOYEE) {
            Employee employee = currentEmployee(actor);
            Company company = companyForEmployee(actor);
            request = requestRepository.findByIdAndEmployeeIdAndCompanyIdAndTenantId(
                            requestId, employee.getId(), company.getId(), company.getTenantId())
                    .orElseThrow(() -> new ApiException("Leave application not found"));
        } else {
            Company company = managedCompany(actor);
            request = requestRepository.findByIdAndCompanyIdAndTenantId(requestId, company.getId(), company.getTenantId())
                    .orElseThrow(() -> new ApiException("Leave application not found"));
        }
        if (request.getSupportingDocumentKey() == null) {
            throw new ApiException("No supporting document is attached");
        }
        try {
            String key = request.getSupportingDocumentKey();
            return new DocumentDownload(documentStorage.read(key), documentStorage.contentType(key));
        } catch (IOException exception) {
            throw new ApiException("The supporting document is unavailable");
        }
    }

    private void applyType(LeaveType type, LeaveTypeRequest input, String code) {
        type.setCode(code);
        type.setName(input.getName().trim());
        type.setPaid(input.isPaid());
        type.setAnnualEntitlement(amount(input.getAnnualEntitlement()));
        type.setCarryForwardLimit(amount(input.getCarryForwardLimit()));
        type.setMaxConsecutiveDays(input.getMaxConsecutiveDays());
        type.setSupportingDocumentRequired(input.isSupportingDocumentRequired());
        type.setAllowPastApplications(input.isAllowPastApplications());
        type.setAllowHalfDay(input.isAllowHalfDay());
        type.setAllowNegativeBalance(input.isAllowNegativeBalance());
    }

    private BigDecimal calculateApplicationDays(
            LeaveType type, LeaveApplicationRequest input, Set<LocalDate> workingDates) {
        if (input.isHalfDay()) {
            if (!type.isAllowHalfDay()) throw new ApiException("Half-day leave is not allowed for this leave type");
            if (!input.getStartDate().equals(input.getEndDate()) || input.getHalfDayPeriod() == null) {
                throw new ApiException("Half-day leave must be one date with a morning or afternoon period");
            }
            if (!workingDates.contains(input.getStartDate())) {
                throw new ApiException("Half-day leave must be on a company working day");
            }
            return HALF_DAY;
        }
        if (input.getHalfDayPeriod() != null) {
            throw new ApiException("Half-day period is only valid for a half-day application");
        }
        BigDecimal days = BigDecimal.valueOf(workingDates.size());
        if (days.signum() == 0) throw new ApiException("The selected period contains no company working days");
        return days;
    }

    private void applyBalanceDeduction(User actor, Company company, Employee employee, LeaveRequest request) {
        LeaveType type = findLeaveType(request.getLeaveTypeId(), company);
        Map<Integer, BigDecimal> yearlyDays = yearlyWorkingDays(company, request);
        for (Map.Entry<Integer, BigDecimal> yearEntry : yearlyDays.entrySet()) {
            LeaveBalance balance = balanceRepository.findForUpdate(employee.getId(), company.getId(),
                            company.getTenantId(), type.getId(), yearEntry.getKey())
                    .orElseThrow(() -> conflict("Leave balance has not been initialized for year " + yearEntry.getKey()));
            if (transactionRepository.findByLeaveRequest_IdAndActionAndLeaveBalance_Id(
                    request.getId(), LeaveLedgerAction.APPROVAL, balance.getId()).isPresent()) {
                throw conflict("Leave balance has already been deducted for this application");
            }
            BigDecimal paidDelta = BigDecimal.ZERO;
            BigDecimal lopDelta = BigDecimal.ZERO;
            if (request.isPaid()) {
                BigDecimal available = allocationTotal(balance).subtract(balance.getPaidUsed());
                if (!type.isAllowNegativeBalance() && available.compareTo(yearEntry.getValue()) < 0) {
                    throw conflict("Insufficient available leave balance for year " + yearEntry.getKey());
                }
                balance.setPaidUsed(balance.getPaidUsed().add(yearEntry.getValue()));
                paidDelta = yearEntry.getValue();
            } else {
                balance.setLopUsed(balance.getLopUsed().add(yearEntry.getValue()));
                lopDelta = yearEntry.getValue();
            }
            balanceRepository.save(balance);
            transactionRepository.save(ledgerTransaction(balance, request, actor, LeaveLedgerAction.APPROVAL,
                    paidDelta, lopDelta,
                    request.isPaid() ? "Approved paid leave" : "Approved unpaid leave (LOP)"));
        }
    }

    private void reverseBalanceDeduction(User actor, Company company, LeaveRequest request) {
        LeaveType type = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ApiException("Leave type not found"));
        for (Map.Entry<Integer, BigDecimal> yearEntry : yearlyWorkingDays(company, request).entrySet()) {
            LeaveBalance balance = balanceRepository.findForUpdate(request.getEmployeeId(), request.getCompanyId(),
                            request.getTenantId(), type.getId(), yearEntry.getKey())
                    .orElseThrow(() -> conflict("Leave balance could not be found for cancellation"));
            LeaveBalanceTransaction original = transactionRepository.findByLeaveRequest_IdAndActionAndLeaveBalance_Id(
                            request.getId(), LeaveLedgerAction.APPROVAL, balance.getId())
                    .orElseThrow(() -> conflict("Original leave deduction could not be found"));
            if (transactionRepository.findByLeaveRequest_IdAndActionAndLeaveBalance_Id(
                    request.getId(), LeaveLedgerAction.CANCELLATION_REVERSAL, balance.getId()).isPresent()) {
                throw conflict("Leave balance has already been restored for this application");
            }
            balance.setPaidUsed(balance.getPaidUsed().subtract(original.getPaidDelta()));
            balance.setLopUsed(balance.getLopUsed().subtract(original.getLopDelta()));
            if (balance.getPaidUsed().signum() < 0 || balance.getLopUsed().signum() < 0) {
                throw conflict("Leave balance history is inconsistent; cancellation was not applied");
            }
            balanceRepository.save(balance);
            transactionRepository.save(ledgerTransaction(balance, request, actor,
                    LeaveLedgerAction.CANCELLATION_REVERSAL,
                    original.getPaidDelta().negate(), original.getLopDelta().negate(),
                    "Approved leave cancellation reversal"));
        }
    }

    private LeaveBalanceTransaction ledgerTransaction(
            LeaveBalance balance, LeaveRequest request, User actor, LeaveLedgerAction action,
            BigDecimal paidDelta, BigDecimal lopDelta, String description) {
        if (request != null && (!Objects.equals(balance.getEmployeeId(), request.getEmployeeId())
                || !Objects.equals(balance.getCompanyId(), request.getCompanyId())
                || !Objects.equals(balance.getTenantId(), request.getTenantId()))) {
            throw new ApiException("Leave request and balance must belong to the same employee and company");
        }
        if (actor == null || actor.getCompany() == null
                || !Objects.equals(balance.getCompanyId(), actor.getCompany().getId())
                || !Objects.equals(balance.getTenantId(), actor.getTenantId())) {
            throw new ApiException("Leave ledger actor must belong to the balance company and tenant");
        }
        return LeaveBalanceTransaction.builder()
                .leaveBalance(balance)
                .leaveRequest(request)
                .actor(actor)
                .action(action)
                .paidDelta(paidDelta)
                .lopDelta(lopDelta)
                .description(description)
                .build();
    }

    private void createLeaveAttendance(User actor, Company company, Employee employee, LeaveRequest request) {
        Map<LocalDate, BigDecimal> leaveDays = workingDayAmounts(company, request);
        AttendanceRule rule = attendanceRuleRepository.findByCompanyIdAndTenantId(
                company.getId(), company.getTenantId()).orElse(null);
        String timeZone = rule == null ? "UTC" : rule.getTimeZoneId();
        LocalDateTime now = LocalDateTime.now(clock);
        for (Map.Entry<LocalDate, BigDecimal> entry : leaveDays.entrySet()) {
            Optional<Attendance> existing = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), entry.getKey());
            if (existing.isPresent()) {
                Attendance record = existing.get();
                if (record.getStatus() == AttendanceStatus.HOLIDAY || record.getStatus() == AttendanceStatus.WEEK_OFF) {
                    continue;
                }
                throw conflict("Attendance already exists for " + entry.getKey()
                        + "; review it before approving leave to avoid overwriting attendance");
            }
            Attendance attendance = Attendance.builder().employeeId(employee.getId())
                    .companyId(company.getId()).tenantId(company.getTenantId()).date(entry.getKey())
                    .status(request.isHalfDay() ? AttendanceStatus.HALF_DAY : AttendanceStatus.ON_LEAVE)
                    .timeZoneId(timeZone).totalWorkingMinutes(0).lateArrival(false).earlyDeparture(false)
                    .overtimeMinutes(0).approvalStatus(AttendanceApprovalStatus.NOT_REQUIRED)
                    .createdBy(actor.getId()).updatedBy(actor.getId()).createdAt(now).updatedAt(now)
                    .leaveRequestId(request.getId()).leaveDayFraction(entry.getValue())
                    .leavePaid(request.isPaid()).build();
            try {
                attendanceRepository.saveAndFlush(attendance);
            } catch (DataIntegrityViolationException exception) {
                throw conflict("Attendance changed while leave approval was being processed");
            }
        }
    }

    private void reverseLeaveAttendance(LeaveRequest request) {
        for (Attendance attendance : attendanceRepository.findByLeaveRequestIdOrderByDateAsc(request.getId())) {
            if (attendance.getCheckInTime() != null || attendance.getCheckOutTime() != null
                    || attendance.getApprovalStatus() != AttendanceApprovalStatus.NOT_REQUIRED
                    || attendance.getStatus() != (request.isHalfDay() ? AttendanceStatus.HALF_DAY : AttendanceStatus.ON_LEAVE)) {
                throw conflict("Leave attendance was changed; resolve it before cancelling this leave");
            }
        }
        attendanceRepository.deleteByLeaveRequestId(request.getId());
    }

    private List<LeaveBalanceResponse> balancesFor(Employee employee, Company company, int year) {
        validateYear(year);
        List<LeaveBalance> balances = balanceRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveYearOrderByLeaveTypeId(
                employee.getId(), company.getId(), company.getTenantId(), year);
        List<LeaveBalanceResponse> result = new ArrayList<>();
        for (LeaveBalance balance : balances) {
            LeaveType type = leaveTypeRepository.findByIdAndCompanyIdAndTenantId(
                    balance.getLeaveTypeId(), company.getId(), company.getTenantId())
                    .orElseThrow(() -> new ApiException("Leave type not found"));
            BigDecimal pending = pendingForYear(employee, company, type, year);
            result.add(balanceResponse(balance, type, pending));
        }
        return result;
    }

    private BigDecimal pendingForYear(Employee employee, Company company, LeaveType type, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest pending : requestRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveTypeIdAndStatus(
                employee.getId(), company.getId(), company.getTenantId(), type.getId(), LeaveRequestStatus.PENDING)) {
            total = total.add(yearlyWorkingDays(company, pending).getOrDefault(year, BigDecimal.ZERO));
        }
        return total;
    }

    private LeaveBalanceResponse balanceResponse(LeaveBalance balance, LeaveType type, BigDecimal pending) {
        return LeaveBalanceResponse.from(balance, type.getCode(), type.getName(), type.isPaid(), pending);
    }

    private LeaveOverviewResponse overview(Company company) {
        return LeaveOverviewResponse.builder().companyId(company.getId())
                .pendingApplications(requestRepository.countByCompanyIdAndTenantIdAndStatus(
                        company.getId(), company.getTenantId(), LeaveRequestStatus.PENDING))
                .approvedApplications(requestRepository.countByCompanyIdAndTenantIdAndStatus(
                        company.getId(), company.getTenantId(), LeaveRequestStatus.APPROVED))
                .rejectedApplications(requestRepository.countByCompanyIdAndTenantIdAndStatus(
                        company.getId(), company.getTenantId(), LeaveRequestStatus.REJECTED))
                .cancellationRequests(requestRepository.countByCompanyIdAndTenantIdAndStatus(
                        company.getId(), company.getTenantId(), LeaveRequestStatus.CANCELLATION_PENDING))
                .activeLeaveTypes(leaveTypeRepository.countByCompanyIdAndTenantIdAndActiveTrue(
                        company.getId(), company.getTenantId()))
                .activeCompanyHolidays(holidayRepository.countByCompanyIdAndTenantIdAndActiveTrue(
                        company.getId(), company.getTenantId())).build();
    }

    private LeaveApplicationResponse response(LeaveRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!Objects.equals(employee.getCompanyId(), request.getCompanyId())
                || !Objects.equals(employee.getTenantId(), request.getTenantId())) {
            throw new AccessDeniedException("Leave application references an employee outside its company");
        }
        return LeaveApplicationResponse.from(request, employee.getFirstName() + " " + employee.getLastName(),
                employee.getEmployeeNumber());
    }

    private Map<Integer, BigDecimal> yearlyWorkingDays(Company company, LeaveRequest request) {
        Map<Integer, BigDecimal> days = new TreeMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : workingDayAmounts(company, request).entrySet()) {
            days.merge(entry.getKey().getYear(), entry.getValue(), BigDecimal::add);
        }
        return days;
    }

    private Map<LocalDate, BigDecimal> workingDayAmounts(Company company, LeaveRequest request) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (LocalDate day : requestWorkingDates(company, request)) {
            result.put(day, request.isHalfDay() ? HALF_DAY : FULL_DAY);
        }
        return result;
    }

    private Set<LocalDate> requestWorkingDates(Company company, LeaveRequest request) {
        if (request.getWorkingDates() == null || request.getWorkingDates().isBlank()) {
            return workingDayDates(company, request.getStartDate(), request.getEndDate());
        }
        try {
            List<LocalDate> parsed = Arrays.stream(request.getWorkingDates().split(","))
                    .map(LocalDate::parse).toList();
            Set<LocalDate> dates = new LinkedHashSet<>(parsed);
            if (dates.size() != parsed.size() || dates.stream().anyMatch(date ->
                    date.isBefore(request.getStartDate()) || date.isAfter(request.getEndDate()))) {
                throw new ApiException("Leave application working-day snapshot is inconsistent");
            }
            return dates;
        } catch (DateTimeException exception) {
            throw new ApiException("Leave application working-day snapshot is invalid");
        }
    }

    private Set<LocalDate> workingDayDates(Company company, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Set<DayOfWeek> weeklyOffs = weeklyHolidays(company);
        Set<LocalDate> companyHolidays = holidayRepository
                .findByCompanyIdAndTenantIdAndDateBetweenAndActiveTrue(company.getId(), company.getTenantId(), from, to)
                .stream().map(CompanyHoliday::getDate).collect(Collectors.toSet());
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!weeklyOffs.contains(date.getDayOfWeek()) && !companyHolidays.contains(date)) dates.add(date);
        }
        return dates;
    }

    private Set<DayOfWeek> weeklyHolidays(Company company) {
        return attendanceRuleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .map(AttendanceRule::getWeeklyHolidays)
                .map(value -> Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .map(day -> DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT))).collect(Collectors.toSet()))
                .orElseGet(() -> EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
    }

    private void assertRequestOwner(LeaveRequest request, Employee employee) {
        if (!Objects.equals(request.getEmployeeId(), employee.getId())
                || !Objects.equals(request.getCompanyId(), employee.getCompanyId())
                || !Objects.equals(request.getTenantId(), employee.getTenantId())) {
            throw new AccessDeniedException("Leave application does not belong to this employee");
        }
    }

    private Employee currentEmployee(User actor) {
        return employeeIdentityService.currentEmployee(actor);
    }

    private Company companyForEmployee(User actor) {
        if (actor.getCompany() == null) throw new AccessDeniedException("Company context missing");
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }

    private Company managedCompany(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        if (actor.getCompany() == null) throw new AccessDeniedException("Company context missing");
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }

    private Employee findEmployee(Long employeeId, Company company) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee does not belong to your company");
        }
        return employee;
    }

    private LeaveType findLeaveType(Long typeId, Company company) {
        return leaveTypeRepository.findByIdAndCompanyIdAndTenantId(typeId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Leave type not found"));
    }

    private CompanyHoliday findHoliday(Long holidayId, Company company) {
        return holidayRepository.findByIdAndCompanyIdAndTenantId(holidayId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Company holiday not found"));
    }

    private LocalDate today(Company company) {
        String timeZone = attendanceRuleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .map(AttendanceRule::getTimeZoneId).orElse("UTC");
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(timeZone)));
        } catch (DateTimeException exception) {
            throw new ApiException("The company's attendance timezone is invalid");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new ApiException("Leave date range is invalid");
        if (from.plusDays(366).isBefore(to)) throw new ApiException("A leave application cannot span more than one year");
    }

    private void validateOptionalDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw new ApiException("Date filter range is invalid");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException("Page must be non-negative and size between 1 and 100");
    }

    private void validateYear(int year) {
        if (year < 2000 || year > 2200) throw new ApiException("Leave year must be between 2000 and 2200");
    }

    private BigDecimal allocationTotal(LeaveBalance balance) {
        return balance.getOpeningBalance().add(balance.getAnnualEntitlement())
                .add(balance.getCarryForward()).add(balance.getAccrued());
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private void audit(User actor, Company company, String action, String details) {
        auditLogService.record(action, actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(), details);
    }

    private void cleanupDocument(String key, Exception cause) {
        if (key == null) return;
        try {
            documentStorage.delete(key);
        } catch (IOException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    public record DocumentDownload(byte[] content, String contentType) { }
}

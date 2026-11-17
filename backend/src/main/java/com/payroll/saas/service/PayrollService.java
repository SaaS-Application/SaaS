package com.payroll.saas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.*;
import com.payroll.saas.entity.*;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.*;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.50");
    private static final BigDecimal FULL_DAY = new BigDecimal("1.00");
    private static final List<LeaveRequestStatus> APPROVED_LEAVE_STATUSES =
            List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCELLATION_PENDING);
    private static final TypeReference<List<PayrollComponentResponse>> COMPONENT_LIST_TYPE = new TypeReference<>() { };

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryAssignmentRepository salaryAssignmentRepository;
    private final SalaryAssignmentComponentRepository salaryAssignmentComponentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceRuleRepository attendanceRuleRepository;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeIdentityService employeeIdentityService;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PayrollPreviewResponse previewPayroll(User actor, String payPeriod) {
        Company company = managedCompany(actor);
        return buildPreview(company, parsePeriod(payPeriod));
    }

    @Transactional
    public PayrollProcessResponse processPayroll(User actor, String payPeriod) {
        Company company = managedCompany(actor);
        YearMonth period = parsePeriod(payPeriod);

        List<Employee> employees = activeEmployees(company, period);
        for (Employee employee : employees) {
            employeeRepository.findForUpdate(employee.getId(), company.getId(), company.getTenantId())
                    .orElseThrow(() -> new ApiException("Employee changed while payroll was being prepared"));
        }

        PayrollPreviewResponse preview = buildPreview(company, period);
        if (!preview.blockingErrors().isEmpty()) {
            throw new ApiException("Payroll cannot be processed: " + String.join("; ", preview.blockingErrors()));
        }

        List<PayrollEmployeePreviewResponse> eligible = preview.employees().stream()
                .filter(PayrollEmployeePreviewResponse::eligible)
                .toList();
        if (eligible.isEmpty()) {
            if (preview.employees().stream().anyMatch(PayrollEmployeePreviewResponse::alreadyProcessed)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payroll is already processed for every eligible employee in this period");
            }
            throw new ApiException("No employees have a complete salary assignment for this period");
        }

        Map<Long, Employee> employeesById = employees.stream()
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
        List<Payroll> payrolls = eligible.stream().map(item -> {
            Employee employee = employeesById.get(item.employeeId());
            return Payroll.builder()
                    .tenantId(company.getTenantId())
                    .companyId(company.getId())
                    .employeeId(item.employeeId())
                    .payPeriod(period.toString())
                    .totalWorkingDays(item.totalWorkingDays())
                    .paidDays(item.paidDays())
                    .unpaidDays(item.unpaidDays())
                    .grossSalary(item.grossEarnings())
                    .deductions(item.totalDeductions())
                    .netSalary(item.netSalary())
                    .status("PROCESSED")
                    .createdAt(LocalDateTime.now(clock))
                    .processedOn(LocalDate.now(clock))
                    .componentBreakdownJson(serializeComponents(item.components()))
                    .payslipNumber("PS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
                    .companyNameSnapshot(company.getName())
                    .companyAddressSnapshot(company.getAddress())
                    .employeeNameSnapshot(employeeName(employee))
                    .employeeNumberSnapshot(employee.getEmployeeNumber())
                    .departmentNameSnapshot(employee.getDepartment() == null
                            ? null : employee.getDepartment().getName())
                    .designationNameSnapshot(employee.getDesignation() == null
                            ? employee.getJobTitle() : employee.getDesignation().getName())
                    .build();
        }).toList();

        List<Payroll> saved;
        try {
            payrolls.forEach(this::validatePayrollPeriodAvailable);
            saved = payrollRepository.saveAllAndFlush(payrolls);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payroll records changed during processing; refresh the preview before retrying");
        }

        auditLogService.record("PAYROLL_PROCESSED", actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(),
                "Processed " + saved.size() + " payroll record(s) for " + period);

        List<PayrollResponse> responses = saved.stream()
                .map(payroll -> toResponse(payroll, employeesById.get(payroll.getEmployeeId())))
                .toList();
        return new PayrollProcessResponse(period.toString(), responses.size(), responses);
    }

    @Transactional(readOnly = true)
    public void validatePayrollPeriodAvailable(Payroll payroll) {
        if (payrollRepository.existsByEmployeeIdAndCompanyIdAndTenantIdAndPayPeriod(
                payroll.getEmployeeId(), payroll.getCompanyId(), payroll.getTenantId(), payroll.getPayPeriod())) {
            throw new ApiException("A payroll record already exists for this employee and pay period");
        }
    }

    @Transactional(readOnly = true)
    public List<PayrollResponse> getCompanyPayrolls(User actor, String payPeriod, Long requestedCompanyId) {
        Company company = managedCompany(actor);
        if (requestedCompanyId != null && !Objects.equals(requestedCompanyId, company.getId())) {
            throw new AccessDeniedException("Cross-company payroll access denied");
        }
        List<Payroll> payrolls;
        if (payPeriod == null || payPeriod.isBlank()) {
            payrolls = payrollRepository.findByCompanyIdAndTenantIdOrderByPayPeriodDescEmployeeIdAsc(
                    company.getId(), company.getTenantId());
        } else {
            String period = parsePeriod(payPeriod).toString();
            payrolls = payrollRepository.findByCompanyIdAndTenantIdAndPayPeriodOrderByEmployeeIdAsc(
                    company.getId(), company.getTenantId(), period);
        }
        return responsesFor(payrolls);
    }

    @Transactional(readOnly = true)
    public PayrollResponse getCompanyPayroll(User actor, Long payrollId) {
        Company company = managedCompany(actor);
        Payroll payroll = payrollRepository.findByIdAndCompanyIdAndTenantId(
                        payrollId, company.getId(), company.getTenantId())
                .orElseThrow(() -> notFound());
        return toResponse(payroll, findEmployee(payroll.getEmployeeId()));
    }

    @Transactional(readOnly = true)
    public List<PayrollResponse> getOwnPayrolls(User actor) {
        Employee employee = currentEmployee(actor);
        Company company = employeeCompany(actor, employee);
        List<Payroll> payrolls = payrollRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByPayPeriodDesc(
                employee.getId(), company.getId(), company.getTenantId());
        return payrolls.stream().map(payroll -> toResponse(payroll, employee)).toList();
    }

    @Transactional(readOnly = true)
    public PayrollResponse getOwnPayroll(User actor, Long payrollId) {
        Employee employee = currentEmployee(actor);
        Company company = employeeCompany(actor, employee);
        Payroll payroll = payrollRepository.findByIdAndEmployeeIdAndCompanyIdAndTenantId(
                        payrollId, employee.getId(), company.getId(), company.getTenantId())
                .orElseThrow(() -> notFound());
        return toResponse(payroll, employee);
    }

    @Transactional(readOnly = true)
    public List<PayrollResponse> getPlatformPayrolls(User actor, Long companyId, String payPeriod) {
        requireSuperAdmin(actor);
        Long tenantId = null;
        if (companyId != null) {
            Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
            tenantId = company.getTenantId();
        }
        String period = payPeriod == null || payPeriod.isBlank() ? null : parsePeriod(payPeriod).toString();
        return responsesFor(payrollRepository.searchPayrolls(companyId, tenantId, period));
    }

    @Transactional(readOnly = true)
    public PayrollResponse getPlatformPayroll(User actor, Long payrollId) {
        requireSuperAdmin(actor);
        Payroll payroll = payrollRepository.findById(payrollId).orElseThrow(() -> notFound());
        return toResponse(payroll, findEmployee(payroll.getEmployeeId()));
    }

    private PayrollPreviewResponse buildPreview(Company company, YearMonth period) {
        List<String> warnings = new ArrayList<>();
        List<String> blockingErrors = new ArrayList<>();
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        if (!periodEnd.isBefore(LocalDate.now(clock))) {
            blockingErrors.add("Only completed pay periods can be processed");
        }

        Optional<AttendanceRule> rule = attendanceRuleRepository.findByCompanyIdAndTenantId(
                company.getId(), company.getTenantId());
        if (rule.isEmpty()) {
            blockingErrors.add("Attendance rules are not configured for this company");
        }

        Set<DayOfWeek> weeklyDaysOff = Set.of();
        boolean validRule = rule.isPresent();
        if (rule.isPresent()) {
            try {
                weeklyDaysOff = parseWeeklyDaysOff(rule.get().getWeeklyHolidays());
            } catch (IllegalArgumentException exception) {
                validRule = false;
                blockingErrors.add("Attendance rules contain an invalid weekly holiday");
            }
        }

        Set<LocalDate> holidays = companyHolidayRepository
                .findByCompanyIdAndTenantIdAndDateBetweenAndActiveTrue(
                        company.getId(), company.getTenantId(), periodStart, periodEnd)
                .stream().map(CompanyHoliday::getDate).collect(Collectors.toSet());
        Set<LocalDate> companyWorkingDates = new LinkedHashSet<>();
        if (validRule) {
            for (LocalDate day = periodStart; !day.isAfter(periodEnd); day = day.plusDays(1)) {
                if (!weeklyDaysOff.contains(day.getDayOfWeek()) && !holidays.contains(day)) {
                    companyWorkingDates.add(day);
                }
            }
        }

        List<Employee> employees = activeEmployees(company, period);
        if (employees.isEmpty()) {
            blockingErrors.add("No active employees are eligible for this pay period");
        }

        Map<Long, Payroll> existingPayrolls = payrollRepository
                .findByCompanyIdAndTenantIdAndPayPeriodOrderByEmployeeIdAsc(
                        company.getId(), company.getTenantId(), period.toString())
                .stream().collect(Collectors.toMap(Payroll::getEmployeeId, payroll -> payroll));
        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findByCompanyIdAndTenantIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        company.getId(), company.getTenantId(), APPROVED_LEAVE_STATUSES, periodEnd, periodStart);
        Map<Long, LeaveRequest> approvedLeavesById = approvedLeaves.stream()
                .collect(Collectors.toMap(LeaveRequest::getId, request -> request));

        List<PayrollEmployeePreviewResponse> rows = new ArrayList<>();
        for (Employee employee : employees) {
            Payroll existing = existingPayrolls.get(employee.getId());
            if (existing != null) {
                rows.add(existingPreview(existing, employee));
                continue;
            }

            List<String> employeeWarnings = new ArrayList<>();
            LocalDate joined = employee.getDateOfJoining();
            if (joined == null) {
                employeeWarnings.add("Employee start date is missing");
                blockingErrors.add(employee.getEmployeeNumber() + ": employee start date is missing");
                rows.add(ineligible(employee, employeeWarnings));
                continue;
            }

            LocalDate employeePeriodStart = joined.isAfter(periodStart) ? joined : periodStart;
            List<LocalDate> workingDates = companyWorkingDates.stream()
                    .filter(day -> !day.isBefore(employeePeriodStart))
                    .toList();
            if (!validRule) {
                employeeWarnings.add("Configured working days cannot be determined");
                rows.add(ineligible(employee, employeeWarnings));
                continue;
            }
            if (workingDates.isEmpty()) {
                employeeWarnings.add("No configured working days exist in this employee's pay period");
                blockingErrors.add(employee.getEmployeeNumber() + ": configured working days are zero");
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, ZERO_MONEY, ZERO_MONEY, ZERO_MONEY,
                        null, null, null, false, false, List.copyOf(employeeWarnings), List.of()));
                continue;
            }

            AttendanceSummary attendanceSummary = summarizeAttendance(
                    company, employee, workingDates, employeePeriodStart, periodEnd, approvedLeavesById);
            employeeWarnings.addAll(attendanceSummary.warnings());
            if (!attendanceSummary.complete()) {
                blockingErrors.add(employee.getEmployeeNumber() + ": attendance is incomplete or unapproved");
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, dayCount(workingDates.size()),
                        attendanceSummary.paidDays(), null, null, null, null,
                        false, false, List.copyOf(employeeWarnings), List.of()));
                continue;
            }

            SalaryAssignment firstAssignment = salaryAssignmentRepository.findCurrent(
                    employee.getId(), company.getId(), company.getTenantId(), workingDates.get(0)).orElse(null);
            SalaryAssignment lastAssignment = salaryAssignmentRepository.findCurrent(
                    employee.getId(), company.getId(), company.getTenantId(),
                    workingDates.get(workingDates.size() - 1)).orElse(null);
            if (firstAssignment == null && lastAssignment == null) {
                employeeWarnings.add("No effective salary assignment covers this pay period");
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, dayCount(workingDates.size()),
                        attendanceSummary.paidDays(), dayCount(workingDates.size()).subtract(attendanceSummary.paidDays()),
                        null, null, null, false, false, List.copyOf(employeeWarnings), List.of()));
                continue;
            }
            if (firstAssignment == null || lastAssignment == null
                    || !Objects.equals(firstAssignment.getId(), lastAssignment.getId())) {
                employeeWarnings.add("One salary assignment must cover all configured working days in the pay period");
                blockingErrors.add(employee.getEmployeeNumber() + ": salary assignment coverage changes during the period");
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, dayCount(workingDates.size()),
                        attendanceSummary.paidDays(), dayCount(workingDates.size()).subtract(attendanceSummary.paidDays()),
                        null, null, null, false, false, List.copyOf(employeeWarnings), List.of()));
                continue;
            }

            try {
                Calculation calculation = calculate(firstAssignment, company, workingDates.size(),
                        attendanceSummary.paidDays());
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, dayCount(workingDates.size()),
                        attendanceSummary.paidDays(),
                        dayCount(workingDates.size()).subtract(attendanceSummary.paidDays()),
                        calculation.grossEarnings(), calculation.deductions(), calculation.netSalary(),
                        true, false, List.copyOf(employeeWarnings), calculation.components()));
            } catch (ApiException exception) {
                employeeWarnings.add(exception.getMessage());
                blockingErrors.add(employee.getEmployeeNumber() + ": salary calculation is invalid");
                rows.add(new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                        employeeName(employee), null, dayCount(workingDates.size()),
                        attendanceSummary.paidDays(),
                        dayCount(workingDates.size()).subtract(attendanceSummary.paidDays()),
                        null, null, null, false, false, List.copyOf(employeeWarnings), List.of()));
            }
        }

        int eligibleCount = (int) rows.stream().filter(PayrollEmployeePreviewResponse::eligible).count();
        int skippedCount = rows.size() - eligibleCount;
        if (rows.stream().anyMatch(row -> row.warnings().stream()
                .anyMatch(warning -> warning.startsWith("No effective salary assignment")))) {
            warnings.add("Employees without an effective salary assignment will be skipped; other eligible employees may be processed.");
        }
        if (rows.stream().anyMatch(PayrollEmployeePreviewResponse::alreadyProcessed)) {
            warnings.add("Payroll records already processed for this period will not be recreated.");
        }
        if (eligibleCount == 0 && blockingErrors.isEmpty()) {
            warnings.add("No employees are eligible for processing in this period.");
        }
        return new PayrollPreviewResponse(period.toString(), eligibleCount, skippedCount,
                blockingErrors.isEmpty() && eligibleCount > 0,
                List.copyOf(warnings), List.copyOf(blockingErrors), List.copyOf(rows));
    }

    private AttendanceSummary summarizeAttendance(
            Company company, Employee employee, List<LocalDate> workingDates,
            LocalDate from, LocalDate to, Map<Long, LeaveRequest> approvedLeavesById) {
        List<Attendance> records = attendanceRepository
                .findByEmployeeIdAndCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                        employee.getId(), company.getId(), company.getTenantId(), from, to);
        Map<LocalDate, Attendance> recordsByDate = records.stream()
                .collect(Collectors.toMap(Attendance::getDate, attendance -> attendance));
        List<String> warnings = new ArrayList<>();
        BigDecimal paidDays = ZERO_MONEY;
        List<LocalDate> missing = new ArrayList<>();
        List<LocalDate> unapproved = new ArrayList<>();

        for (LocalDate day : workingDates) {
            Attendance attendance = recordsByDate.get(day);
            if (attendance == null) {
                missing.add(day);
                continue;
            }
            if (attendance.getApprovalStatus() != AttendanceApprovalStatus.NOT_REQUIRED
                    && attendance.getApprovalStatus() != AttendanceApprovalStatus.APPROVED) {
                unapproved.add(day);
                continue;
            }
            try {
                paidDays = paidDays.add(paidFraction(attendance, employee, company, approvedLeavesById));
            } catch (ApiException exception) {
                warnings.add(day + ": " + exception.getMessage());
            }
        }

        if (!missing.isEmpty()) {
            warnings.add(formatDateIssue("Missing attendance", missing));
        }
        if (!unapproved.isEmpty()) {
            warnings.add(formatDateIssue("Attendance requires approval", unapproved));
        }
        boolean complete = missing.isEmpty() && unapproved.isEmpty()
                && warnings.isEmpty() && paidDays.compareTo(dayCount(workingDates.size())) <= 0;
        if (paidDays.compareTo(dayCount(workingDates.size())) > 0) {
            warnings.add("Paid days exceed configured working days");
        }
        return new AttendanceSummary(complete, paidDays.setScale(2, RoundingMode.HALF_UP), List.copyOf(warnings));
    }

    private BigDecimal paidFraction(
            Attendance attendance, Employee employee, Company company,
            Map<Long, LeaveRequest> approvedLeavesById) {
        return switch (attendance.getStatus()) {
            case PRESENT, LATE -> {
                if (attendance.getLeaveRequestId() != null) {
                    throw new ApiException("non-leave attendance references a leave request");
                }
                yield FULL_DAY;
            }
            case ABSENT -> {
                if (attendance.getLeaveRequestId() != null) {
                    throw new ApiException("absence attendance unexpectedly references a leave request");
                }
                yield ZERO_MONEY;
            }
            case HALF_DAY -> {
                if (attendance.getLeaveRequestId() == null) {
                    yield HALF_DAY;
                }
                LeaveRequest leave = approvedLeave(attendance, employee, company, approvedLeavesById);
                if (!leave.isHalfDay() || attendance.getLeaveDayFraction() == null
                        || HALF_DAY.compareTo(attendance.getLeaveDayFraction()) != 0) {
                    throw new ApiException("half-day leave attendance does not match its approved request");
                }
                yield attendance.getLeavePaid() ? FULL_DAY : HALF_DAY;
            }
            case ON_LEAVE -> {
                LeaveRequest leave = approvedLeave(attendance, employee, company, approvedLeavesById);
                if (leave.isHalfDay()) {
                    throw new ApiException("full-day leave attendance does not match its approved request");
                }
                BigDecimal fraction = attendance.getLeaveDayFraction();
                if (fraction == null || fraction.compareTo(BigDecimal.ZERO) <= 0
                        || fraction.compareTo(FULL_DAY) > 0) {
                    throw new ApiException("leave attendance has an invalid day fraction");
                }
                yield attendance.getLeavePaid() ? fraction : ZERO_MONEY;
            }
            case HOLIDAY, WEEK_OFF -> throw new ApiException(
                    "attendance marks a configured working day as a non-working day");
        };
    }

    private LeaveRequest approvedLeave(
            Attendance attendance, Employee employee, Company company,
            Map<Long, LeaveRequest> approvedLeavesById) {
        if (attendance.getLeaveRequestId() == null || attendance.getLeavePaid() == null) {
            throw new ApiException("leave attendance is missing its approved leave reference or paid status");
        }
        LeaveRequest leave = approvedLeavesById.get(attendance.getLeaveRequestId());
        if (leave == null || !Objects.equals(leave.getEmployeeId(), employee.getId())
                || !Objects.equals(leave.getCompanyId(), company.getId())
                || !Objects.equals(leave.getTenantId(), company.getTenantId())
                || leave.isPaid() != attendance.getLeavePaid()) {
            throw new ApiException("leave attendance does not match an approved request for this employee");
        }
        return leave;
    }

    private Calculation calculate(
            SalaryAssignment assignment, Company company, int workingDays, BigDecimal paidDays) {
        List<SalaryAssignmentComponent> assignedComponents =
                salaryAssignmentComponentRepository.findBySalaryAssignmentIdAndCompanyIdAndTenantIdOrderById(
                        assignment.getId(), company.getId(), company.getTenantId());
        if (assignedComponents.isEmpty()) {
            throw new ApiException("Salary assignment has no component snapshots");
        }

        BigDecimal denominator = dayCount(workingDays);
        List<PayrollComponentResponse> components = new ArrayList<>();
        BigDecimal gross = ZERO_MONEY;
        BigDecimal deductions = ZERO_MONEY;
        for (SalaryAssignmentComponent component : assignedComponents) {
            if (component.getType() == null || component.getCalculatedAmount() == null) {
                throw new ApiException("Salary assignment contains an incomplete component snapshot");
            }
            BigDecimal monthly = SalaryStructureService.validateMonetaryAmount(
                    component.getCalculatedAmount(), "Monthly salary component amount");
            BigDecimal payable = component.getType() == SalaryComponentType.EARNING
                    ? monthly.multiply(paidDays).divide(denominator, 2, RoundingMode.HALF_UP)
                    : monthly;
            payable = SalaryStructureService.validateMonetaryAmount(
                    payable.setScale(2, RoundingMode.HALF_UP), "Payroll component amount");
            if (component.getType() == SalaryComponentType.EARNING) {
                gross = gross.add(payable);
            } else {
                deductions = deductions.add(payable);
            }
            components.add(new PayrollComponentResponse(
                    component.getCode(), component.getName(), component.getType(), monthly, payable));
        }

        gross = SalaryStructureService.validateMonetaryAmount(
                gross.setScale(2, RoundingMode.HALF_UP), "Gross earnings");
        deductions = SalaryStructureService.validateMonetaryAmount(
                deductions.setScale(2, RoundingMode.HALF_UP), "Total deductions");
        BigDecimal net = SalaryStructureService.validateMonetaryAmount(
                gross.subtract(deductions).setScale(2, RoundingMode.HALF_UP), "Net salary");
        return new Calculation(gross, deductions, net, List.copyOf(components));
    }

    private List<Employee> activeEmployees(Company company, YearMonth period) {
        return employeeRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId()).stream()
                .filter(employee -> "ACTIVE".equalsIgnoreCase(employee.getStatus()))
                .filter(employee -> employee.getDateOfJoining() == null
                        || !employee.getDateOfJoining().isAfter(period.atEndOfMonth()))
                .sorted(Comparator.comparing(Employee::getId))
                .toList();
    }

    private PayrollEmployeePreviewResponse existingPreview(Payroll payroll, Employee employee) {
        return new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                employeeName(employee), payroll.getId(), payroll.getTotalWorkingDays(), payroll.getPaidDays(),
                payroll.getUnpaidDays(), payroll.getGrossSalary(), payroll.getDeductions(), payroll.getNetSalary(),
                false, true, List.of("Payroll has already been processed for this employee and period"),
                deserializeComponents(payroll));
    }

    private PayrollEmployeePreviewResponse ineligible(Employee employee, List<String> warnings) {
        return new PayrollEmployeePreviewResponse(employee.getId(), employee.getEmployeeNumber(),
                employeeName(employee), null, null, null, null, null, null, null,
                false, false, List.copyOf(warnings), List.of());
    }

    private List<PayrollResponse> responsesFor(List<Payroll> payrolls) {
        if (payrolls.isEmpty()) return List.of();
        Map<Long, Employee> employees = employeeRepository.findAllById(
                        payrolls.stream().map(Payroll::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, employee -> employee));
        return payrolls.stream()
                .map(payroll -> toResponse(payroll, employees.get(payroll.getEmployeeId())))
                .toList();
    }

    private PayrollResponse toResponse(Payroll payroll, Employee employee) {
        if (employee == null || !Objects.equals(employee.getCompanyId(), payroll.getCompanyId())
                || !Objects.equals(employee.getTenantId(), payroll.getTenantId())) {
            throw new ApiException("Payroll record references an employee outside its company");
        }
        return new PayrollResponse(payroll.getId(), payroll.getCompanyId(), payroll.getPayPeriod(), employee.getId(),
                employee.getEmployeeNumber(), employeeName(employee), payroll.getTotalWorkingDays(),
                payroll.getPaidDays(), payroll.getUnpaidDays(), payroll.getGrossSalary(),
                payroll.getDeductions(), payroll.getNetSalary(), payroll.getStatus(),
                payroll.getCreatedAt(), payroll.getProcessedOn(), deserializeComponents(payroll));
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Payroll employee not found"));
    }

    private Employee currentEmployee(User actor) {
        if (actor == null || actor.getRole() != UserRole.EMPLOYEE) {
            throw new AccessDeniedException("Employee role required");
        }
        return employeeIdentityService.currentEmployee(actor);
    }

    private Company employeeCompany(User actor, Employee employee) {
        Company company = tenantSecurityService.assertCompanyAccess(actor, employee.getCompanyId());
        if (!Objects.equals(employee.getTenantId(), company.getTenantId())
                || !Objects.equals(actor.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee payroll tenant mismatch");
        }
        tenantSecurityService.assertEmployeeAccess(actor, employee);
        return company;
    }

    private Company managedCompany(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        if (actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }
        Company company = tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
        if (!Objects.equals(actor.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Tenant mismatch detected");
        }
        return company;
    }

    private void requireSuperAdmin(User actor) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
    }

    private YearMonth parsePeriod(String payPeriod) {
        try {
            YearMonth parsed = YearMonth.parse(payPeriod);
            if (!parsed.toString().equals(payPeriod)) {
                throw new DateTimeParseException("Invalid pay period", payPeriod, 0);
            }
            return parsed;
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ApiException("Pay period must use the YYYY-MM format");
        }
    }

    private Set<DayOfWeek> parseWeeklyDaysOff(String configuredDays) {
        if (configuredDays == null) {
            throw new IllegalArgumentException("Weekly holidays are not configured");
        }
        Set<DayOfWeek> daysOff = EnumSet.noneOf(DayOfWeek.class);
        for (String value : configuredDays.split(",")) {
            if (!value.isBlank()) {
                daysOff.add(DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            }
        }
        return daysOff;
    }

    private String serializeComponents(List<PayrollComponentResponse> components) {
        try {
            return objectMapper.writeValueAsString(components);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payroll component breakdown", exception);
        }
    }

    private List<PayrollComponentResponse> deserializeComponents(Payroll payroll) {
        if (payroll.getComponentBreakdownJson() == null || payroll.getComponentBreakdownJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payroll.getComponentBreakdownJson(), COMPONENT_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored payroll component breakdown is invalid", exception);
        }
    }

    private String formatDateIssue(String label, List<LocalDate> dates) {
        String sample = dates.stream().limit(3).map(LocalDate::toString).collect(Collectors.joining(", "));
        String suffix = dates.size() > 3 ? ", ..." : "";
        return label + " on " + sample + suffix + " (" + dates.size() + " day(s))";
    }

    private BigDecimal dayCount(int days) {
        return BigDecimal.valueOf(days).setScale(2, RoundingMode.UNNECESSARY);
    }

    private String employeeName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll record not found");
    }

    private record AttendanceSummary(boolean complete, BigDecimal paidDays, List<String> warnings) { }

    private record Calculation(
            BigDecimal grossEarnings,
            BigDecimal deductions,
            BigDecimal netSalary,
            List<PayrollComponentResponse> components) { }
}

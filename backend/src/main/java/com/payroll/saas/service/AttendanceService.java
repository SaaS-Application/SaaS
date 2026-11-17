package com.payroll.saas.service;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.*;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.AttendanceRepository;
import com.payroll.saas.repository.AttendanceRuleRepository;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceRuleRepository ruleRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeIdentityService employeeIdentityService;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public AttendanceRecordResponse checkIn(User actor, String notes) {
        requireRole(actor, UserRole.EMPLOYEE);
        Company company = currentCompany(actor);
        Employee employee = employeeForUser(actor, company);
        requireActive(employee);
        AttendanceRule rules = rulesFor(company);
        ZoneId zone = zone(rules);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);

        if (attendanceRepository.existsByEmployeeIdAndDate(employee.getId(), now.toLocalDate())) {
            throw conflict("Attendance has already been recorded for today");
        }

        boolean weeklyHoliday = weeklyHolidays(rules).contains(now.getDayOfWeek());
        boolean late = !weeklyHoliday && isLate(now.toLocalTime(), rules);
        Attendance attendance = Attendance.builder()
                .tenantId(company.getTenantId())
                .companyId(company.getId())
                .employeeId(employee.getId())
                .date(now.toLocalDate())
                .checkInTime(now)
                .checkInInstant(clock.instant())
                .timeZoneId(rules.getTimeZoneId())
                .status(weeklyHoliday ? AttendanceStatus.WEEK_OFF : late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .lateArrival(late)
                .notes(clean(notes))
                .approvalStatus(AttendanceApprovalStatus.NOT_REQUIRED)
                .createdBy(actor.getId())
                .updatedBy(actor.getId())
                .createdAt(nowUtc())
                .updatedAt(nowUtc())
                .build();
        return saveUnique(attendance, employee);
    }

    @Transactional
    public AttendanceRecordResponse checkOut(User actor) {
        requireRole(actor, UserRole.EMPLOYEE);
        Company company = currentCompany(actor);
        Employee employee = employeeForUser(actor, company);
        requireActive(employee);
        AttendanceRule rules = rulesFor(company);
        ZoneId zone = zone(rules);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        Attendance attendance = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), now.toLocalDate())
                .orElseThrow(() -> new ApiException("Check in before checking out"));

        if (attendance.getCheckOutTime() != null) {
            throw conflict("Attendance has already been checked out");
        }
        if (attendance.getCheckInTime() == null) {
            throw new ApiException("Attendance has no check-in time");
        }
        if (attendance.getApprovalStatus() == AttendanceApprovalStatus.APPROVED) {
            throw new AccessDeniedException("Approved attendance cannot be changed by an employee");
        }

        attendance.setCheckOutTime(now);
        attendance.setCheckOutInstant(clock.instant());
        attendance.setUpdatedBy(actor.getId());
        applyCalculations(attendance, rules);
        auditLogService.record("ATTENDANCE_CHECK_OUT", actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(), "Employee checked out for " + attendance.getDate());
        return AttendanceRecordResponse.from(attendanceRepository.save(attendance), employee);
    }

    @Transactional
    public AttendanceRecordResponse createManual(User actor, ManualAttendanceRequest request) {
        Company company = managedCompany(actor);
        Employee employee = findEmployee(request.getEmployeeId(), actor, company);
        requireActive(employee);
        if (request.getDate().isAfter(today(company))) {
            throw new ApiException("Attendance cannot be entered for a future date");
        }
        validatePunchPair(request.getStatus(), request.getCheckInTime(), request.getCheckOutTime());
        AttendanceRule rules = rulesFor(company);
        LocalDateTime checkIn = localDateTime(request.getDate(), request.getCheckInTime(), rules);
        LocalDateTime checkOut = localDateTime(request.getDate(), request.getCheckOutTime(), rules);
        Attendance attendance = Attendance.builder()
                .tenantId(company.getTenantId())
                .companyId(company.getId())
                .employeeId(employee.getId())
                .date(request.getDate())
                .checkInTime(checkIn)
                .checkInInstant(instant(request.getDate(), request.getCheckInTime(), rules))
                .checkOutTime(checkOut)
                .checkOutInstant(instant(request.getDate(), request.getCheckOutTime(), rules))
                .timeZoneId(rules.getTimeZoneId())
                .status(request.getStatus())
                .notes(clean(request.getNotes()))
                .approvalStatus(AttendanceApprovalStatus.PENDING)
                .createdBy(actor.getId())
                .updatedBy(actor.getId())
                .createdAt(nowUtc())
                .updatedAt(nowUtc())
                .build();
        calculateManualStatus(attendance, request.getStatus(), rules);
        AttendanceRecordResponse response = saveUnique(attendance, employee);
        audit(actor, company, "ATTENDANCE_MANUAL_CREATED", "Record " + attendance.getId() + " entered for employee " + employee.getId());
        return response;
    }

    @Transactional
    public AttendanceRecordResponse correct(User actor, Long attendanceId, AttendanceCorrectionRequest request) {
        Company company = managedCompany(actor);
        Attendance attendance = findOwnedAttendance(attendanceId, company);
        if (attendance.getLeaveRequestId() != null) {
            throw conflict("Leave-related attendance must be cancelled through the leave workflow before correction");
        }
        Employee employee = findEmployee(attendance.getEmployeeId(), actor, company);
        validatePunchPair(request.getStatus(), request.getCheckInTime(), request.getCheckOutTime());
        AttendanceRule rules = rulesFor(company);
        attendance.setCheckInTime(localDateTime(attendance.getDate(), request.getCheckInTime(), rules));
        attendance.setCheckInInstant(instant(attendance.getDate(), request.getCheckInTime(), rules));
        attendance.setCheckOutTime(localDateTime(attendance.getDate(), request.getCheckOutTime(), rules));
        attendance.setCheckOutInstant(instant(attendance.getDate(), request.getCheckOutTime(), rules));
        attendance.setTimeZoneId(rules.getTimeZoneId());
        attendance.setStatus(request.getStatus());
        attendance.setNotes(clean(request.getNotes()));
        attendance.setApprovalStatus(AttendanceApprovalStatus.PENDING);
        attendance.setApprovedBy(null);
        attendance.setUpdatedBy(actor.getId());
        calculateManualStatus(attendance, request.getStatus(), rules);
        AttendanceRecordResponse response = AttendanceRecordResponse.from(attendanceRepository.save(attendance), employee);
        audit(actor, company, "ATTENDANCE_CORRECTED", "Record " + attendanceId + " correction submitted for approval");
        return response;
    }

    @Transactional
    public AttendanceRecordResponse decideApproval(User actor, Long attendanceId, AttendanceApprovalRequest request) {
        Company company = managedCompany(actor);
        Attendance attendance = findOwnedAttendance(attendanceId, company);
        if (attendance.getApprovalStatus() != AttendanceApprovalStatus.PENDING) {
            throw conflict("Only pending attendance entries can be approved or rejected");
        }
        attendance.setApprovalStatus(request.getDecision() == AttendanceApprovalDecision.APPROVE
                ? AttendanceApprovalStatus.APPROVED : AttendanceApprovalStatus.REJECTED);
        attendance.setApprovedBy(request.getDecision() == AttendanceApprovalDecision.APPROVE ? actor.getId() : null);
        attendance.setUpdatedBy(actor.getId());
        AttendanceRecordResponse response = AttendanceRecordResponse.from(attendanceRepository.save(attendance),
                employeeRepository.findById(attendance.getEmployeeId()).orElseThrow(() -> new ApiException("Employee not found")));
        audit(actor, company, "ATTENDANCE_" + request.getDecision().name(),
                "Record " + attendanceId + " " + request.getDecision().name().toLowerCase(Locale.ROOT) + "d");
        return response;
    }

    @Transactional(readOnly = true)
    public Page<AttendanceRecordResponse> daily(User actor, LocalDate date, String search,
                                                Long departmentId, AttendanceStatus status,
                                                int page, int size) {
        Company company = managedCompany(actor);
        validatePage(page, size);
        Page<Attendance> results = attendanceRepository.searchCompanyDay(company.getId(), company.getTenantId(),
                date, departmentId, clean(search), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "employeeId")));
        return results.map(this::response);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> companyDay(User actor, Long companyId, LocalDate date) {
        requireRole(actor, UserRole.SUPER_ADMIN);
        Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
        return attendanceRepository.findByCompanyIdAndTenantIdAndDateOrderByEmployeeIdAsc(
                        company.getId(), company.getTenantId(), date).stream()
                .map(this::response).toList();
    }

    @Transactional
    public List<AttendanceRecordResponse> ownHistory(User actor, LocalDate from, LocalDate to) {
        requireRole(actor, UserRole.EMPLOYEE);
        Company company = currentCompany(actor);
        Employee employee = employeeForUser(actor, company);
        validateDateRange(from, to);
        return attendanceRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                        employee.getId(), company.getId(), company.getTenantId(), from, to).stream()
                .map(attendance -> AttendanceRecordResponse.from(attendance, employee)).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> employeeHistory(User actor, Long employeeId, LocalDate from, LocalDate to) {
        Company company = managedCompany(actor);
        Employee employee = findEmployee(employeeId, actor, company);
        validateDateRange(from, to);
        return attendanceRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                        employee.getId(), company.getId(), company.getTenantId(), from, to).stream()
                .map(attendance -> AttendanceRecordResponse.from(attendance, employee)).toList();
    }

    @Transactional
    public AttendanceMonthlySummaryResponse ownMonthly(User actor, String month) {
        requireRole(actor, UserRole.EMPLOYEE);
        Company company = currentCompany(actor);
        Employee employee = employeeForUser(actor, company);
        return summarize(parseMonth(month), attendanceRepository
                .findByEmployeeIdAndCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                        employee.getId(), company.getId(), company.getTenantId(),
                        parseMonth(month).atDay(1), parseMonth(month).atEndOfMonth()));
    }

    @Transactional(readOnly = true)
    public AttendanceMonthlySummaryResponse companyMonthly(User actor, String month) {
        Company company = managedCompany(actor);
        YearMonth yearMonth = parseMonth(month);
        List<Attendance> records = attendanceRepository.findByCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                company.getId(), company.getTenantId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return summarize(yearMonth, records);
    }

    @Transactional(readOnly = true)
    public List<AttendanceEmployeeReportResponse> companyReport(User actor, LocalDate from, LocalDate to,
                                                                 Long departmentId) {
        Company company = managedCompany(actor);
        validateDateRange(from, to);
        List<Attendance> records = attendanceRepository.findByCompanyIdAndTenantIdAndDateBetweenOrderByDateAsc(
                company.getId(), company.getTenantId(), from, to);
        Map<Long, Employee> employees = employeeRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .stream().filter(employee -> departmentId == null
                        || employee.getDepartment() != null && departmentId.equals(employee.getDepartment().getId()))
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
        Map<Long, List<Attendance>> grouped = records.stream()
                .filter(record -> employees.containsKey(record.getEmployeeId()))
                .collect(Collectors.groupingBy(Attendance::getEmployeeId, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(entry -> employeeReport(employees.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceRuleResponse getRules(User actor) {
        Company company = managedCompany(actor);
        return AttendanceRuleResponse.from(ruleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .orElseGet(() -> defaultRules(company)));
    }

    @Transactional
    public AttendanceRuleResponse updateRules(User actor, AttendanceRuleRequest request) {
        Company company = managedCompany(actor);
        validateRules(request);
        AttendanceRule rules = ruleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .orElseGet(() -> defaultRules(company));
        rules.setWorkStartTime(request.getWorkStartTime());
        rules.setWorkEndTime(request.getWorkEndTime());
        rules.setRequiredWorkingMinutes(request.getRequiredWorkingMinutes());
        rules.setGracePeriodMinutes(request.getGracePeriodMinutes());
        rules.setHalfDayThresholdMinutes(request.getHalfDayThresholdMinutes());
        rules.setOvertimeThresholdMinutes(request.getOvertimeThresholdMinutes());
        rules.setWeeklyHolidays(normalizeWeeklyHolidays(request.getWeeklyHolidays()));
        rules.setTimeZoneId(request.getTimeZoneId().trim());
        return AttendanceRuleResponse.from(ruleRepository.save(rules));
    }

    private AttendanceRecordResponse saveUnique(Attendance attendance, Employee employee) {
        if (attendanceRepository.existsByEmployeeIdAndDate(attendance.getEmployeeId(), attendance.getDate())) {
            throw conflict("Attendance already exists for this employee and date");
        }
        try {
            return AttendanceRecordResponse.from(attendanceRepository.saveAndFlush(attendance), employee);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Attendance already exists for this employee and date");
        }
    }

    private AttendanceRecordResponse response(Attendance attendance) {
        Employee employee = employeeRepository.findById(attendance.getEmployeeId())
                .orElseThrow(() -> new ApiException("Employee not found"));
        return AttendanceRecordResponse.from(attendance, employee);
    }

    private Attendance findOwnedAttendance(Long id, Company company) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new ApiException("Attendance record not found"));
        if (!Objects.equals(attendance.getCompanyId(), company.getId())
                || !Objects.equals(attendance.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Attendance record does not belong to your company");
        }
        return attendance;
    }

    private Employee findEmployee(Long employeeId, User actor, Company company) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));
        tenantSecurityService.assertEmployeeAccess(actor, employee);
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee does not belong to your company");
        }
        return employee;
    }

    private Employee employeeForUser(User actor, Company company) {
        Employee employee = employeeIdentityService.currentEmployee(actor);
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee profile does not belong to your company");
        }
        tenantSecurityService.assertEmployeeAccess(actor, employee);
        return employee;
    }

    private void requireActive(Employee employee) {
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Inactive employees cannot use attendance");
        }
    }

    private Company currentCompany(User actor) {
        if (actor == null || actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }

    private Company managedCompany(User actor) {
        requireRole(actor, UserRole.COMPANY_ADMIN, UserRole.HR_ADMIN);
        return currentCompany(actor);
    }

    private void requireRole(User actor, UserRole... roles) {
        if (actor == null || Arrays.stream(roles).noneMatch(role -> role == actor.getRole())) {
            throw new AccessDeniedException("Role is not authorized for this attendance operation");
        }
    }

    private AttendanceRule rulesFor(Company company) {
        return ruleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .orElseGet(() -> ruleRepository.save(defaultRules(company)));
    }

    private AttendanceRule defaultRules(Company company) {
        return AttendanceRule.builder()
                .tenantId(company.getTenantId())
                .companyId(company.getId())
                .workStartTime(LocalTime.of(9, 0))
                .workEndTime(LocalTime.of(17, 0))
                .requiredWorkingMinutes(480)
                .gracePeriodMinutes(15)
                .halfDayThresholdMinutes(240)
                .overtimeThresholdMinutes(30)
                .weeklyHolidays("SATURDAY,SUNDAY")
                .timeZoneId("UTC")
                .build();
    }

    private void calculateManualStatus(Attendance attendance, AttendanceStatus requestedStatus, AttendanceRule rules) {
        validatePunchPair(requestedStatus,
                attendance.getCheckInTime() == null ? null : attendance.getCheckInTime().toLocalTime(),
                attendance.getCheckOutTime() == null ? null : attendance.getCheckOutTime().toLocalTime());
        if (attendance.getCheckInTime() == null) {
            attendance.setTotalWorkingMinutes(0);
            attendance.setOvertimeMinutes(0);
            attendance.setLateArrival(requestedStatus == AttendanceStatus.LATE);
            attendance.setEarlyDeparture(false);
            return;
        }
        applyCalculations(attendance, rules);
    }

    private void applyCalculations(Attendance attendance, AttendanceRule rules) {
        if (attendance.getCheckInTime() == null || attendance.getCheckOutTime() == null) {
            attendance.setTotalWorkingMinutes(0);
            attendance.setOvertimeMinutes(0);
            attendance.setEarlyDeparture(false);
            return;
        }
        ZoneId zone = attendance.getTimeZoneId() == null ? zone(rules) : ZoneId.of(attendance.getTimeZoneId());
        Instant start = attendance.getCheckInInstant() == null
                ? attendance.getCheckInTime().atZone(zone).toInstant() : attendance.getCheckInInstant();
        Instant end = attendance.getCheckOutInstant() == null
                ? attendance.getCheckOutTime().atZone(zone).toInstant() : attendance.getCheckOutInstant();
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0 || minutes > 24 * 60) {
            throw new ApiException("Check-out must be after check-in and within 24 hours");
        }
        attendance.setTotalWorkingMinutes((int) minutes);
        boolean nonWorkingStatus = attendance.getStatus() == AttendanceStatus.ABSENT
                || attendance.getStatus() == AttendanceStatus.ON_LEAVE
                || attendance.getStatus() == AttendanceStatus.HOLIDAY
                || attendance.getStatus() == AttendanceStatus.WEEK_OFF;
        attendance.setLateArrival(!nonWorkingStatus && isLate(attendance.getCheckInTime().toLocalTime(), rules));
        attendance.setEarlyDeparture(!nonWorkingStatus && attendance.getCheckOutTime().toLocalDate().equals(attendance.getDate())
                && attendance.getCheckOutTime().toLocalTime().isBefore(rules.getWorkEndTime()));
        int overtime = Math.max(0, (int) minutes - rules.getRequiredWorkingMinutes());
        attendance.setOvertimeMinutes(overtime >= rules.getOvertimeThresholdMinutes() ? overtime : 0);

        if (attendance.getStatus() != AttendanceStatus.ABSENT
                && attendance.getStatus() != AttendanceStatus.ON_LEAVE
                && attendance.getStatus() != AttendanceStatus.HOLIDAY
                && attendance.getStatus() != AttendanceStatus.WEEK_OFF) {
            attendance.setStatus(minutes < rules.getHalfDayThresholdMinutes()
                    ? AttendanceStatus.HALF_DAY
                    : attendance.isLateArrival() ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        }
    }

    private void validatePunchPair(AttendanceStatus status, LocalTime checkIn, LocalTime checkOut) {
        if ((checkIn == null) != (checkOut == null)) {
            throw new ApiException("Check-in and check-out times must be provided together");
        }
        if (checkIn != null && !checkOut.isAfter(checkIn)) {
            throw new ApiException("Check-out must be after check-in");
        }
        if (checkIn != null && (status == AttendanceStatus.ABSENT
                || status == AttendanceStatus.ON_LEAVE || status == AttendanceStatus.HOLIDAY)) {
            throw new ApiException("Absent, leave, and holiday records cannot include punch times");
        }
    }

    private boolean isLate(LocalTime checkIn, AttendanceRule rules) {
        return checkIn.isAfter(rules.getWorkStartTime().plusMinutes(rules.getGracePeriodMinutes()));
    }

    private Set<DayOfWeek> weeklyHolidays(AttendanceRule rules) {
        return Arrays.stream(rules.getWeeklyHolidays().split(","))
                .filter(day -> !day.isBlank())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private String normalizeWeeklyHolidays(List<String> days) {
        try {
            EnumSet<DayOfWeek> normalized = days.stream()
                    .map(day -> DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
            return normalized.stream().map(Enum::name).collect(Collectors.joining(","));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekly holidays must be day names such as SATURDAY");
        }
    }

    private void validateRules(AttendanceRuleRequest request) {
        if (!request.getWorkStartTime().isBefore(request.getWorkEndTime())) {
            throw new ApiException("Work start time must be earlier than work end time");
        }
        int scheduledMinutes = (int) Duration.between(request.getWorkStartTime(), request.getWorkEndTime()).toMinutes();
        if (request.getRequiredWorkingMinutes() > scheduledMinutes) {
            throw new ApiException("Required working minutes cannot exceed the scheduled work window");
        }
        if (request.getGracePeriodMinutes() >= scheduledMinutes) {
            throw new ApiException("Grace period must be shorter than the scheduled work window");
        }
        if (request.getHalfDayThresholdMinutes() > request.getRequiredWorkingMinutes()) {
            throw new ApiException("Half-day threshold cannot exceed required working minutes");
        }
        try {
            ZoneId.of(request.getTimeZoneId().trim());
            normalizeWeeklyHolidays(request.getWeeklyHolidays());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone");
        }
    }

    private LocalDateTime localDateTime(LocalDate date, LocalTime time, AttendanceRule rules) {
        if (time == null) {
            return null;
        }
        instant(date, time, rules);
        return date.atTime(time);
    }

    private Instant instant(LocalDate date, LocalTime time, AttendanceRule rules) {
        if (time == null) {
            return null;
        }
        LocalDateTime localDateTime = date.atTime(time);
        List<ZoneOffset> offsets = zone(rules).getRules().getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            throw new ApiException("The selected local time does not exist because of a daylight-saving transition");
        }
        return localDateTime.atOffset(offsets.get(0)).toInstant();
    }

    private ZoneId zone(AttendanceRule rules) {
        return ZoneId.of(rules.getTimeZoneId());
    }

    private LocalDate today(Company company) {
        AttendanceRule rules = ruleRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .orElseGet(() -> defaultRules(company));
        return LocalDate.now(clock.withZone(zone(rules)));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AttendanceMonthlySummaryResponse summarize(YearMonth month, List<Attendance> records) {
        return AttendanceMonthlySummaryResponse.builder()
                .month(month)
                .presentDays(count(records, AttendanceStatus.PRESENT))
                .absentDays(count(records, AttendanceStatus.ABSENT))
                .halfDays(count(records, AttendanceStatus.HALF_DAY))
                .lateDays(count(records, AttendanceStatus.LATE))
                .lateArrivals((int) records.stream().filter(Attendance::isLateArrival).count())
                .leaveDays(count(records, AttendanceStatus.ON_LEAVE))
                .holidayDays(count(records, AttendanceStatus.HOLIDAY))
                .weekOffDays(count(records, AttendanceStatus.WEEK_OFF))
                .totalWorkingMinutes(records.stream().mapToInt(Attendance::getTotalWorkingMinutes).sum())
                .overtimeMinutes(records.stream().mapToInt(Attendance::getOvertimeMinutes).sum())
                .build();
    }

    private int count(List<Attendance> records, AttendanceStatus status) {
        return (int) records.stream().filter(record -> record.getStatus() == status).count();
    }

    private AttendanceEmployeeReportResponse employeeReport(Employee employee, List<Attendance> records) {
        return AttendanceEmployeeReportResponse.builder()
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .recordedDays(records.size())
                .presentDays((int) records.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT
                        || record.getStatus() == AttendanceStatus.LATE || record.getStatus() == AttendanceStatus.HALF_DAY).count())
                .absentDays(count(records, AttendanceStatus.ABSENT))
                .halfDays(count(records, AttendanceStatus.HALF_DAY))
                .lateArrivals((int) records.stream().filter(Attendance::isLateArrival).count())
                .leaveDays(count(records, AttendanceStatus.ON_LEAVE))
                .totalWorkingMinutes(records.stream().mapToInt(Attendance::getTotalWorkingMinutes).sum())
                .overtimeMinutes(records.stream().mapToInt(Attendance::getOvertimeMinutes).sum())
                .build();
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must use YYYY-MM format");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range must have a start on or before its end");
        }
        if (from.plusYears(1).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed one year");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size between 1 and 100");
        }
    }

    private void audit(User actor, Company company, String action, String details) {
        auditLogService.record(action, actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(), details);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

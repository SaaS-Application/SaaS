package com.payroll.saas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.LoginRequest;
import com.payroll.saas.dto.PayrollComponentResponse;
import com.payroll.saas.entity.*;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.*;
import com.payroll.saas.service.AuditLogService;
import com.payroll.saas.service.PayrollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollProcessingIntegrationTest {

    private static final String PASSWORD = "Safe@Test123";
    private static final List<String> WEEKLY_DAYS_OFF =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PayrollService payrollService;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceRuleRepository attendanceRuleRepository;
    @Autowired private CompanyHolidayRepository companyHolidayRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private LeaveBalanceTransactionRepository leaveBalanceTransactionRepository;
    @Autowired private SalaryAssignmentRepository salaryAssignmentRepository;
    @Autowired private SalaryAssignmentComponentRepository salaryAssignmentComponentRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private SalaryStructureComponentRepository salaryStructureComponentRepository;
    @Autowired private SalaryComponentDefinitionRepository salaryComponentDefinitionRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @SpyBean private AuditLogService auditLogService;

    private Company companyA;
    private Company companyB;
    private User superAdmin;
    private User companyAdmin;
    private User hrAdmin;
    private User companyBAdmin;
    private User employeeUser;
    private User companyBEmployeeUser;
    private User employeeWithoutSalaryUser;
    private Employee employeeA;
    private Employee employeeB;
    private Employee employeeWithoutSalary;
    private YearMonth period;

    @BeforeEach
    void setUp() {
        payrollRepository.deleteAll();
        attendanceRepository.deleteAll();
        leaveBalanceTransactionRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        companyHolidayRepository.deleteAll();
        attendanceRuleRepository.deleteAll();
        salaryAssignmentComponentRepository.deleteAll();
        salaryAssignmentRepository.deleteAll();
        salaryStructureComponentRepository.deleteAll();
        salaryStructureRepository.deleteAll();
        salaryComponentDefinitionRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        designationRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        period = YearMonth.from(LocalDate.now(Clock.systemUTC())).minusMonths(1);
        companyA = saveCompany("Acme Payroll", "ACME-PAY", 81001L);
        companyB = saveCompany("Beta Payroll", "BETA-PAY", 82002L);
        superAdmin = saveUser("payroll-super", "super@payroll.test", UserRole.SUPER_ADMIN, null, 0L);
        companyAdmin = saveUser("payroll-admin", "admin@acme-payroll.test",
                UserRole.COMPANY_ADMIN, companyA, companyA.getTenantId());
        hrAdmin = saveUser("payroll-hr", "hr@acme-payroll.test",
                UserRole.HR_ADMIN, companyA, companyA.getTenantId());
        companyBAdmin = saveUser("payroll-admin-b", "admin@beta-payroll.test",
                UserRole.COMPANY_ADMIN, companyB, companyB.getTenantId());
        employeeUser = saveUser("payroll-employee", "employee@acme-payroll.test",
                UserRole.EMPLOYEE, companyA, companyA.getTenantId());
        companyBEmployeeUser = saveUser("payroll-employee-b", "employee@beta-payroll.test",
                UserRole.EMPLOYEE, companyB, companyB.getTenantId());
        employeeWithoutSalaryUser = saveUser("payroll-employee-2", "employee2@acme-payroll.test",
                UserRole.EMPLOYEE, companyA, companyA.getTenantId());
        employeeA = saveEmployee(companyA, "PAY-001", employeeUser);
        employeeB = saveEmployee(companyB, "PAY-B-001", companyBEmployeeUser);
        auditLogRepository.deleteAll();
    }

    @Test
    void calculatesMonthlyEarningsWithLopAndKeepsAssignedDeductionsUnchanged() throws Exception {
        createPayrollFixture(employeeA, companyAdmin, true);

        String periodText = period.toString();
        var preview = payrollService.previewPayroll(companyAdmin, periodText);
        var employeePreview = preview.employees().get(0);
        int workingDays = sundaysInPeriod(employeeA);
        BigDecimal expectedGross = new BigDecimal("1000.01")
                .multiply(BigDecimal.valueOf(workingDays - 1L))
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);

        assertThat(preview.processingAllowed()).isTrue();
        assertThat(employeePreview.totalWorkingDays()).isEqualByComparingTo(BigDecimal.valueOf(workingDays));
        assertThat(employeePreview.paidDays()).isEqualByComparingTo(BigDecimal.valueOf(workingDays - 1L));
        assertThat(employeePreview.unpaidDays()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(employeePreview.grossEarnings()).isEqualByComparingTo(expectedGross);
        assertThat(employeePreview.totalDeductions()).isEqualByComparingTo("100.00");
        assertThat(employeePreview.netSalary()).isEqualByComparingTo(expectedGross.subtract(new BigDecimal("100.00")));
        assertThat(employeePreview.components()).hasSize(2);

        String token = login(companyAdmin);
        mockMvc.perform(get("/api/company-admin/payroll-periods/{period}/preview", periodText)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees[0].eligible").value(true))
                .andExpect(jsonPath("$.employees[0].grossEarnings").value(expectedGross.doubleValue()));

        mockMvc.perform(post("/api/company-admin/payroll-periods/{period}/process", periodText)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processedCount").value(1))
                .andExpect(jsonPath("$.payrolls[0].components.length()").value(2));

        Payroll saved = payrollRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByPayPeriodDesc(
                employeeA.getId(), companyA.getId(), companyA.getTenantId()).get(0);
        assertThat(saved.getTotalWorkingDays()).isEqualByComparingTo(BigDecimal.valueOf(workingDays));
        assertThat(saved.getUnpaidDays()).isEqualByComparingTo("1.00");
        assertThat(saved.getGrossSalary()).isEqualByComparingTo(expectedGross);
        assertThat(saved.getDeductions()).isEqualByComparingTo("100.00");
        assertThat(saved.getStatus()).isEqualTo("PROCESSED");
        assertThat(auditLogRepository.findAll()).anyMatch(log -> "PAYROLL_PROCESSED".equals(log.getAction()));
    }

    @Test
    void incompleteAttendanceBlocksTheWholeBatchBeforeAnyPayrollIsSaved() {
        createPayrollFixture(employeeA, companyAdmin, true);
        LocalDate missingDate = workingDates(employeeA).get(0);
        attendanceRepository.delete(attendanceRepository.findByEmployeeIdAndDate(employeeA.getId(), missingDate)
                .orElseThrow());

        var preview = payrollService.previewPayroll(companyAdmin, period.toString());
        assertThat(preview.processingAllowed()).isFalse();
        assertThat(preview.blockingErrors()).anyMatch(error -> error.contains("attendance is incomplete"));
        assertThatThrownBy(() -> payrollService.processPayroll(companyAdmin, period.toString()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payroll cannot be processed");
        assertThat(payrollRepository.count()).isZero();
    }

    @Test
    void missingSalaryAssignmentIsReportedAndOtherEligibleEmployeesCanBeProcessed() {
        employeeWithoutSalary = saveEmployee(companyA, "PAY-002", employeeWithoutSalaryUser);
        createPayrollFixture(employeeA, companyAdmin, true);
        createPresentAttendance(employeeWithoutSalary, companyAdmin);

        var preview = payrollService.previewPayroll(companyAdmin, period.toString());
        assertThat(preview.processingAllowed()).isTrue();
        assertThat(preview.eligibleCount()).isEqualTo(1);
        assertThat(preview.skippedCount()).isEqualTo(1);
        assertThat(preview.employees()).anyMatch(row -> row.employeeId().equals(employeeWithoutSalary.getId())
                && !row.eligible() && row.warnings().stream()
                .anyMatch(warning -> warning.contains("No effective salary assignment")));

        var processed = payrollService.processPayroll(companyAdmin, period.toString());
        assertThat(processed.processedCount()).isEqualTo(1);
        assertThat(payrollRepository.existsByEmployeeIdAndCompanyIdAndTenantIdAndPayPeriod(
                employeeA.getId(), companyA.getId(), companyA.getTenantId(), period.toString())).isTrue();
        assertThat(payrollRepository.existsByEmployeeIdAndCompanyIdAndTenantIdAndPayPeriod(
                employeeWithoutSalary.getId(), companyA.getId(), companyA.getTenantId(), period.toString())).isFalse();
    }

    @Test
    void preventsDuplicateEmployeePeriodProcessing() {
        createPayrollFixture(employeeA, companyAdmin, true);
        payrollService.processPayroll(companyAdmin, period.toString());

        assertThatThrownBy(() -> payrollService.processPayroll(companyAdmin, period.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(payrollRepository.count()).isEqualTo(1);
    }

    @Test
    void companyAndEmployeePayrollDetailsAreRestrictedToTheirOwners() throws Exception {
        createPayrollFixture(employeeA, companyAdmin, true);
        payrollService.processPayroll(companyAdmin, period.toString());
        Payroll saved = payrollRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByPayPeriodDesc(
                employeeA.getId(), companyA.getId(), companyA.getTenantId()).get(0);
        assertThat(saved).isNotNull();

        mockMvc.perform(get("/api/company-admin/payrolls/{id}", saved.getId())
                        .header("Authorization", "Bearer " + login(companyBAdmin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/payrolls/{id}", saved.getId())
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeA.getId()));
        mockMvc.perform(get("/api/employee/payrolls/{id}", saved.getId())
                        .header("Authorization", "Bearer " + login(companyBEmployeeUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void suspendedCompanyCannotReadOrProcessPayroll() throws Exception {
        createPayrollFixture(employeeA, companyAdmin, true);
        String token = login(hrAdmin);
        companyA.setStatus("SUSPENDED");
        companyRepository.save(companyA);

        mockMvc.perform(get("/api/company-admin/payrolls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> payrollService.processPayroll(companyAdmin, period.toString()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(payrollRepository.count()).isZero();
    }

    @Test
    void superAdminCanReadButCannotProcessPayroll() throws Exception {
        createPayrollFixture(employeeA, companyAdmin, true);
        payrollService.processPayroll(companyAdmin, period.toString());

        mockMvc.perform(get("/api/super-admin/payrolls")
                        .header("Authorization", "Bearer " + login(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value(companyA.getId()));
        assertThatThrownBy(() -> payrollService.processPayroll(superAdmin, period.toString()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void auditFailureRollsBackAllPayrollRowsInTheBatch() {
        createPayrollFixture(employeeA, companyAdmin, true);
        doThrow(new IllegalStateException("test audit failure")).when(auditLogService).record(
                eq("PAYROLL_PROCESSED"), anyString(), eq(UserRole.COMPANY_ADMIN.name()),
                eq(companyA.getTenantId()), eq(companyA.getId()), anyString());

        assertThatThrownBy(() -> payrollService.processPayroll(companyAdmin, period.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test audit failure");
        assertThat(payrollRepository.count()).isZero();
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    void employeeCanDownloadPdfContainingSavedPayrollAndProfileSnapshots() throws Exception {
        Department department = departmentRepository.save(Department.builder()
                .tenantId(companyA.getTenantId()).companyId(companyA.getId()).name("Operations").active(true).build());
        Designation designation = designationRepository.save(Designation.builder()
                .tenantId(companyA.getTenantId()).companyId(companyA.getId()).name("Consultant").active(true).build());
        employeeA.setDepartment(department);
        employeeA.setDesignation(designation);
        employeeRepository.saveAndFlush(employeeA);
        processPayrollFor(employeeA, companyAdmin);

        Long payrollId = payrollFor(employeeA).getId();
        var result = mockMvc.perform(get("/api/employee/payslips/{payrollId}/download", payrollId)
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("payslip-")))
                .andReturn();

        String text = extractPdfText(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("Acme Payroll", "Company Street", "Payroll PAY-001", "PAY-001")
                .contains("Operations", "Consultant", "EARNINGS", "BASE", "DEDUCTIONS", "FIXED-DEDUCT")
                .contains("Total working days", "Paid days", "Unpaid / LOP days")
                .contains("Gross earnings", "Total deductions", "NET SALARY", "INR 100.00")
                .contains("payslip number");
        assertThat(text).doesNotContain("Employee ID:", "Payroll ID:");
    }

    @Test
    void companyAndEmployeeDownloadsAreRestrictedToTheirPayrollOwnership() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Long payrollId = payrollFor(employeeA).getId();

        mockMvc.perform(get("/api/company-admin/payslips/{payrollId}/download", payrollId)
                        .header("Authorization", "Bearer " + login(companyBAdmin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/payslips/{payrollId}/download", payrollId)
                        .header("Authorization", "Bearer " + login(companyBEmployeeUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employee/payslips/{payrollId}/download", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void suspendedCompanyBlocksEmployeeAndCompanyPayslipAccess() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Long payrollId = payrollFor(employeeA).getId();
        companyA.setStatus("SUSPENDED");
        companyRepository.saveAndFlush(companyA);

        mockMvc.perform(get("/api/company-admin/payslips/{payrollId}/download", payrollId)
                        .header("Authorization", "Bearer " + login(companyAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employee/payslips")
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void draftPayrollCannotGenerateAPayslip() throws Exception {
        Payroll draft = payrollRepository.saveAndFlush(Payroll.builder()
                .tenantId(companyA.getTenantId()).companyId(companyA.getId()).employeeId(employeeA.getId())
                .payPeriod(period.toString()).grossSalary(BigDecimal.ZERO).deductions(BigDecimal.ZERO)
                .netSalary(BigDecimal.ZERO).status("DRAFT").createdAt(LocalDateTime.now(Clock.systemUTC()))
                .build());

        mockMvc.perform(get("/api/employee/payslips/{payrollId}/download", draft.getId())
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void superAdminCanReadPayslipsButHasNoWriteOperation() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Long payrollId = payrollFor(employeeA).getId();
        String token = login(superAdmin);

        mockMvc.perform(get("/api/super-admin/companies/{companyId}/payslips", companyA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].downloadable").value(true));
        mockMvc.perform(get("/api/super-admin/companies/{companyId}/payslips/{payrollId}/download",
                        companyA.getId(), payrollId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        mockMvc.perform(post("/api/super-admin/companies/{companyId}/payslips", companyA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void missingHistoricalComponentSnapshotReturnsConflictWithoutReconstruction() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Payroll payroll = payrollFor(employeeA);
        payroll.setComponentBreakdownJson(null);
        payrollRepository.saveAndFlush(payroll);

        mockMvc.perform(get("/api/company-admin/payslips/{payrollId}/download", payroll.getId())
                        .header("Authorization", "Bearer " + login(companyAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("component snapshot is missing")));
    }

    @Test
    void legacyProfileFallbackIsClearlyMarkedInPdf() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Payroll payroll = payrollFor(employeeA);
        payroll.setPayslipNumber(null);
        payroll.setCompanyNameSnapshot(null);
        payroll.setCompanyAddressSnapshot(null);
        payroll.setEmployeeNameSnapshot(null);
        payroll.setEmployeeNumberSnapshot(null);
        payroll.setDepartmentNameSnapshot(null);
        payroll.setDesignationNameSnapshot(null);
        payrollRepository.saveAndFlush(payroll);

        var result = mockMvc.perform(get("/api/company-admin/payslips/{payrollId}/download", payroll.getId())
                        .header("Authorization", "Bearer " + login(companyAdmin)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(extractPdfText(result.getResponse().getContentAsByteArray()))
                .contains("profile details below reflect current records")
                .contains("Acme Payroll", "Payroll PAY-001");
    }

    @Test
    void largeComponentBreakdownPaginatesAndLongNamesDoNotFailPdfGeneration() throws Exception {
        processPayrollFor(employeeA, companyAdmin);
        Payroll payroll = payrollFor(employeeA);
        List<PayrollComponentResponse> components = new ArrayList<>(
                objectMapper.readValue(payroll.getComponentBreakdownJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PayrollComponentResponse.class)));
        String longName = "Long component name ".repeat(9);
        for (int index = 0; index < 55; index++) {
            components.add(new PayrollComponentResponse("EXTRA-" + index, longName,
                    SalaryComponentType.EARNING, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        payroll.setComponentBreakdownJson(objectMapper.writeValueAsString(components));
        payrollRepository.saveAndFlush(payroll);

        var result = mockMvc.perform(get("/api/company-admin/payslips/{payrollId}/download", payroll.getId())
                        .header("Authorization", "Bearer " + login(companyAdmin)))
                .andExpect(status().isOk())
                .andReturn();
        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void employeePayslipListCanBeFilteredByPayPeriod() throws Exception {
        processPayrollFor(employeeA, companyAdmin);

        mockMvc.perform(get("/api/employee/payslips")
                        .param("payPeriod", period.toString())
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payPeriod").value(period.toString()));
    }

    private void processPayrollFor(Employee employee, User actor) {
        createPayrollFixture(employee, actor, true);
        payrollService.processPayroll(actor, period.toString());
    }

    private Payroll payrollFor(Employee employee) {
        return payrollRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByPayPeriodDesc(
                employee.getId(), employee.getCompanyId(), employee.getTenantId()).get(0);
    }

    private String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private void createPayrollFixture(Employee employee, User assignedBy, boolean includeLeaves) {
        createAttendanceRule(companyA);
        List<LocalDate> workdays = workingDates(employee);
        int leaveIndex = 0;
        for (LocalDate day : workdays) {
            if (includeLeaves && leaveIndex < 2) {
                boolean paid = leaveIndex == 0;
                LeaveRequest leave = saveApprovedLeave(employee, assignedBy, day, paid);
                attendanceRepository.saveAndFlush(Attendance.builder()
                        .tenantId(employee.getTenantId()).companyId(employee.getCompanyId())
                        .employeeId(employee.getId()).date(day).status(AttendanceStatus.ON_LEAVE)
                        .timeZoneId("UTC").totalWorkingMinutes(0).lateArrival(false).earlyDeparture(false)
                        .overtimeMinutes(0).approvalStatus(AttendanceApprovalStatus.NOT_REQUIRED)
                        .leaveRequestId(leave.getId()).leaveDayFraction(new BigDecimal("1.00"))
                        .leavePaid(paid).createdBy(assignedBy.getId()).updatedBy(assignedBy.getId())
                        .createdAt(LocalDateTime.now(Clock.systemUTC()))
                        .updatedAt(LocalDateTime.now(Clock.systemUTC())).build());
                leaveIndex++;
            } else {
                attendanceRepository.saveAndFlush(presentAttendance(employee, assignedBy, day));
            }
        }

        SalaryComponentDefinition earning = saveComponent(employee, "BASE", SalaryComponentType.EARNING);
        SalaryComponentDefinition deduction = saveComponent(employee, "FIXED-DEDUCT", SalaryComponentType.DEDUCTION);
        SalaryStructure structure = salaryStructureRepository.save(SalaryStructure.builder()
                .tenantId(employee.getTenantId()).companyId(employee.getCompanyId()).code("MONTHLY")
                .name("Monthly structure").effectiveFrom(period.atDay(1).minusYears(1)).active(true)
                .createdAt(LocalDateTime.now(Clock.systemUTC())).updatedAt(LocalDateTime.now(Clock.systemUTC()))
                .build());
        SalaryAssignment assignment = salaryAssignmentRepository.save(SalaryAssignment.builder()
                .tenantId(employee.getTenantId()).companyId(employee.getCompanyId()).employeeId(employee.getId())
                .salaryStructureId(structure.getId()).structureCodeSnapshot(structure.getCode())
                .structureNameSnapshot(structure.getName()).effectiveFrom(period.atDay(1).minusYears(1))
                .active(true).grossEarnings(new BigDecimal("1000.01"))
                .totalDeductions(new BigDecimal("100.00")).illustrativeNet(new BigDecimal("900.01"))
                .assignedBy(assignedBy.getId()).createdAt(LocalDateTime.now(Clock.systemUTC())).build());
        salaryAssignmentComponentRepository.saveAll(List.of(
                assignmentComponent(assignment, earning, new BigDecimal("1000.01")),
                assignmentComponent(assignment, deduction, new BigDecimal("100.00"))));
        salaryAssignmentComponentRepository.flush();
    }

    private SalaryAssignmentComponent assignmentComponent(
            SalaryAssignment assignment, SalaryComponentDefinition definition, BigDecimal amount) {
        return SalaryAssignmentComponent.builder().tenantId(assignment.getTenantId())
                .companyId(assignment.getCompanyId()).salaryAssignmentId(assignment.getId())
                .componentId(definition.getId()).code(definition.getCode()).name(definition.getName())
                .type(definition.getType()).calculationMethod(SalaryCalculationMethod.FIXED_AMOUNT)
                .value(amount).taxable(definition.isTaxable()).calculatedAmount(amount).build();
    }

    private SalaryComponentDefinition saveComponent(
            Employee employee, String code, SalaryComponentType type) {
        return salaryComponentDefinitionRepository.save(SalaryComponentDefinition.builder()
                .tenantId(employee.getTenantId()).companyId(employee.getCompanyId()).code(code)
                .name(code).type(type).calculationMethod(SalaryCalculationMethod.FIXED_AMOUNT)
                .taxable(false).active(true).effectiveFrom(period.atDay(1).minusYears(1))
                .createdAt(LocalDateTime.now(Clock.systemUTC()))
                .updatedAt(LocalDateTime.now(Clock.systemUTC())).build());
    }

    private void createAttendanceRule(Company company) {
        attendanceRuleRepository.save(AttendanceRule.builder()
                .tenantId(company.getTenantId()).companyId(company.getId())
                .workStartTime(java.time.LocalTime.of(9, 0)).workEndTime(java.time.LocalTime.of(17, 0))
                .requiredWorkingMinutes(480).gracePeriodMinutes(15).halfDayThresholdMinutes(240)
                .overtimeThresholdMinutes(30).weeklyHolidays(String.join(",", WEEKLY_DAYS_OFF))
                .timeZoneId("UTC").build());
    }

    private LeaveRequest saveApprovedLeave(Employee employee, User reviewer, LocalDate day, boolean paid) {
        return leaveRequestRepository.save(LeaveRequest.builder().tenantId(employee.getTenantId())
                .companyId(employee.getCompanyId()).employeeId(employee.getId()).leaveType("TEST")
                .startDate(day).endDate(day).days(BigDecimal.ONE).workingDates(day.toString())
                .paid(paid).halfDay(false).status(LeaveRequestStatus.APPROVED).reason("Payroll test leave")
                .reviewedBy(reviewer.getId()).reviewedAt(LocalDateTime.now(Clock.systemUTC()))
                .createdAt(LocalDateTime.now(Clock.systemUTC())).updatedAt(LocalDateTime.now(Clock.systemUTC()))
                .build());
    }

    private Attendance presentAttendance(Employee employee, User recorder, LocalDate day) {
        return Attendance.builder().tenantId(employee.getTenantId()).companyId(employee.getCompanyId())
                .employeeId(employee.getId()).date(day).status(AttendanceStatus.PRESENT)
                .timeZoneId("UTC").totalWorkingMinutes(480).lateArrival(false).earlyDeparture(false)
                .overtimeMinutes(0).approvalStatus(AttendanceApprovalStatus.NOT_REQUIRED)
                .createdBy(recorder.getId()).updatedBy(recorder.getId())
                .createdAt(LocalDateTime.now(Clock.systemUTC()))
                .updatedAt(LocalDateTime.now(Clock.systemUTC())).build();
    }

    private void createPresentAttendance(Employee employee, User recorder) {
        List<LocalDate> dates = workingDates(employee);
        for (LocalDate day : dates) {
            attendanceRepository.saveAndFlush(presentAttendance(employee, recorder, day));
        }
    }

    private List<LocalDate> workingDates(Employee employee) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate day = period.atDay(1); !day.isAfter(period.atEndOfMonth()); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY
                    && !day.isBefore(employee.getDateOfJoining())) {
                dates.add(day);
            }
        }
        return dates;
    }

    private int sundaysInPeriod(Employee employee) {
        return workingDates(employee).size();
    }

    private Company saveCompany(String name, String code, Long tenantId) {
        return companyRepository.save(Company.builder().name(name).code(code)
                .email("info@" + code.toLowerCase() + ".test").phone("+1000000000")
                .address("Company Street").tenantId(tenantId).subscriptionPlan("PRO")
                .status("ACTIVE").createdAt(LocalDateTime.now(Clock.systemUTC())).build());
    }

    private User saveUser(String username, String email, UserRole role, Company company, Long tenantId) {
        return userRepository.save(User.builder().username(username).email(email)
                .password(passwordEncoder.encode(PASSWORD)).role(role).company(company)
                .tenantId(tenantId).enabled(true).accountNonLocked(true)
                .createdAt(LocalDateTime.now(Clock.systemUTC())).build());
    }

    private Employee saveEmployee(Company company, String number, User user) {
        return employeeRepository.save(Employee.builder().firstName("Payroll").lastName(number)
                .employeeNumber(number).email(user.getEmail()).phone("+1555000000").address("Home")
                .tenantId(company.getTenantId()).companyId(company.getId()).user(user)
                .dateOfJoining(period.atDay(1).minusMonths(2)).employmentStatus("FULL_TIME")
                .jobTitle("Engineer").status("ACTIVE").createdAt(period.atDay(1).minusMonths(2)).build());
    }

    private String login(User user) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(PASSWORD);
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}

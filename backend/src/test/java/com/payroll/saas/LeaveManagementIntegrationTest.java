package com.payroll.saas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.LoginRequest;
import com.payroll.saas.entity.*;
import com.payroll.saas.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceRuleRepository attendanceRuleRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveBalanceTransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Company companyA;
    private Company companyB;
    private User companyAdmin;
    private User hrAdmin;
    private User employeeUser;
    private User secondEmployeeUser;
    private User companyBAdmin;
    private Employee employeeA;
    private Employee secondEmployeeA;
    private Employee employeeB;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        transactionRepository.deleteAll();
        requestRepository.deleteAll();
        balanceRepository.deleteAll();
        holidayRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        attendanceRuleRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        designationRepository.deleteAll();
        departmentRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Acme", "ACME-LEAVE", 51001L);
        companyB = saveCompany("Beta", "BETA-LEAVE", 62002L);
        companyAdmin = saveUser("admin-a", "admin@acme-leave.test", UserRole.COMPANY_ADMIN, companyA);
        hrAdmin = saveUser("hr-a", "hr@acme-leave.test", UserRole.HR_ADMIN, companyA);
        employeeUser = saveUser("employee-a", "ava@acme-leave.test", UserRole.EMPLOYEE, companyA);
        secondEmployeeUser = saveUser("employee-a2", "owen@acme-leave.test", UserRole.EMPLOYEE, companyA);
        companyBAdmin = saveUser("admin-b", "admin@beta-leave.test", UserRole.COMPANY_ADMIN, companyB);
        employeeA = saveEmployee(companyA, "AC-001", employeeUser);
        secondEmployeeA = saveEmployee(companyA, "AC-002", secondEmployeeUser);
        employeeB = saveEmployee(companyB, "BE-001",
                saveUser("employee-b", "ben@beta-leave.test", UserRole.EMPLOYEE, companyB));
    }

    private Company saveCompany(String name, String code, Long tenantId) {
        return companyRepository.save(Company.builder().name(name).code(code)
                .email("info@" + code.toLowerCase() + ".test").phone("+1000000000")
                .address("Company Street").tenantId(tenantId).subscriptionPlan("PRO")
                .status("ACTIVE").createdAt(LocalDateTime.now()).build());
    }

    private User saveUser(String username, String email, UserRole role, Company company) {
        return userRepository.save(User.builder().username(username).email(email)
                .password(passwordEncoder.encode("Safe@Test123")).role(role).company(company)
                .tenantId(company.getTenantId()).enabled(true).accountNonLocked(true)
                .createdAt(LocalDateTime.now()).build());
    }

    private Employee saveEmployee(Company company, String number, User user) {
        return employeeRepository.save(Employee.builder().firstName("Ava").lastName("Adams")
                .employeeNumber(number).email(user.getEmail()).phone("+1555000000").address("Home Street")
                .tenantId(company.getTenantId()).companyId(company.getId()).user(user)
                .dateOfJoining(LocalDate.now().minusMonths(2)).employmentStatus("FULL_TIME")
                .jobTitle("Engineer").status("ACTIVE").createdAt(LocalDate.now()).build());
    }

    private String login(User user) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("Safe@Test123");
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createPaidType(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", "ANNUAL", "name", "Annual leave", "paid", true,
                "annualEntitlement", new BigDecimal("10.00"),
                "carryForwardLimit", new BigDecimal("5.00"),
                "maxConsecutiveDays", 30, "allowHalfDay", true,
                "allowPastApplications", false, "supportingDocumentRequired", false,
                "allowNegativeBalance", false));
        String response = mockMvc.perform(post("/api/company-admin/leave/types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long allocate(String token, long employeeId, long typeId, int year, String days) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", employeeId, "leaveTypeId", typeId, "leaveYear", year,
                "openingBalance", new BigDecimal("0.00"),
                "annualEntitlement", new BigDecimal(days),
                "carryForward", new BigDecimal("0.00"), "accrued", new BigDecimal("0.00"),
                "comment", "Annual allocation"));
        String response = mockMvc.perform(post("/api/company-admin/leave/balances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("employeeId").asLong();
    }

    private String applyBody(long typeId, LocalDate from, LocalDate to) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "leaveTypeId", typeId, "startDate", from, "endDate", to,
                "halfDay", false, "reason", "Personal leave"));
    }

    private LocalDate nextWeekday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek().getValue() > 5) date = date.plusDays(1);
        return date;
    }

    @Test
    void employeeApplicationIsScopedAndOverlapsAreRejected() throws Exception {
        String hrToken = login(hrAdmin);
        long typeId = createPaidType(hrToken);
        String employeeToken = login(employeeUser);
        LocalDate day = nextWeekday();

        String response = mockMvc.perform(post("/api/employee/leave/applications")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody(typeId, day, day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.days").value(1.0))
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/employee/leave/applications")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody(typeId, day, day)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/employee/leave/applications/{id}", requestId)
                        .header("Authorization", "Bearer " + login(secondEmployeeUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/company-admin/leave/applications")
                        .header("Authorization", "Bearer " + login(companyAdmin))
                        .param("employeeId", employeeB.getId().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/company-admin/leave/applications/{id}", requestId)
                        .header("Authorization", "Bearer " + login(companyBAdmin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/company-admin/leave/applications")
                        .header("Authorization", "Bearer " + login(companyAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void approvalDeductsBalanceAndWritesAttendanceThenCancellationReversesBoth() throws Exception {
        String hrToken = login(hrAdmin);
        long typeId = createPaidType(hrToken);
        LocalDate firstDay = nextWeekday();
        LocalDate secondDay = firstDay.plusDays(1);
        while (secondDay.getDayOfWeek().getValue() > 5) secondDay = secondDay.plusDays(1);

        allocate(hrToken, employeeA.getId(), typeId, firstDay.getYear(), "10.00");
        String employeeToken = login(employeeUser);
        String application = mockMvc.perform(post("/api/employee/leave/applications")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(typeId, firstDay, secondDay)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days").value(2.0))
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(application).get("id").asLong();
        holidayRepository.saveAndFlush(CompanyHoliday.builder().companyId(companyA.getId())
                .tenantId(companyA.getTenantId()).name("Recently added holiday").date(firstDay)
                .active(true).build());

        mockMvc.perform(patch("/api/company-admin/leave/applications/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        LeaveBalance balance = balanceRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveTypeIdAndLeaveYear(
                employeeA.getId(), companyA.getId(), companyA.getTenantId(), typeId, firstDay.getYear()).orElseThrow();
        assertThat(balance.getPaidUsed()).isEqualByComparingTo("2.00");
        assertThat(attendanceRepository.findByLeaveRequestIdOrderByDateAsc(requestId)).hasSize(2);
        assertThat(transactionRepository.findByLeaveRequest_IdAndAction(
                requestId, LeaveLedgerAction.APPROVAL)).isPresent();

        mockMvc.perform(patch("/api/employee/leave/applications/{id}/cancellation", requestId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Plans changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));
        mockMvc.perform(patch("/api/company-admin/leave/applications/{id}/cancellation", requestId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"comment\":\"Cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getPaidUsed())
                .isEqualByComparingTo("0.00");
        assertThat(attendanceRepository.findByLeaveRequestIdOrderByDateAsc(requestId)).isEmpty();
        assertThat(transactionRepository.findByLeaveRequest_IdAndAction(
                requestId, LeaveLedgerAction.CANCELLATION_REVERSAL)).isPresent();
    }

    @Test
    void unlinkedEmployeeAndInactiveCompanyCannotUseLeaveFeatures() throws Exception {
        employeeA.setUser(null);
        employeeRepository.saveAndFlush(employeeA);
        String token = login(employeeUser);
        mockMvc.perform(get("/api/employee/leave/applications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        employeeA.setUser(employeeUser);
        employeeRepository.saveAndFlush(employeeA);
        String adminToken = login(companyAdmin);
        companyA.setStatus("SUSPENDED");
        companyRepository.saveAndFlush(companyA);
        mockMvc.perform(get("/api/employee/leave/types")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/company-admin/leave/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotUseManagementEndpointsAndSuperAdminReadsOnly() throws Exception {
        String employeeToken = login(employeeUser);
        mockMvc.perform(post("/api/company-admin/leave/types")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        User superAdmin = userRepository.save(User.builder().username("platform-super")
                .email("super@leave.test").password(passwordEncoder.encode("Safe@Test123"))
                .role(UserRole.SUPER_ADMIN).tenantId(0L).enabled(true).accountNonLocked(true)
                .createdAt(LocalDateTime.now()).build());
        mockMvc.perform(get("/api/super-admin/companies/{id}/leave/overview", companyA.getId())
                        .header("Authorization", "Bearer " + login(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyA.getId()));
        mockMvc.perform(post("/api/super-admin/companies/{id}/leave/types", companyA.getId())
                        .header("Authorization", "Bearer " + login(superAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellationApprovalCannotReverseLeaveAfterItsStartDate() throws Exception {
        String hrToken = login(hrAdmin);
        long typeId = createPaidType(hrToken);
        LocalDate leaveDate = nextWeekday();
        allocate(hrToken, employeeA.getId(), typeId, leaveDate.getYear(), "5.00");
        String employeeToken = login(employeeUser);
        String application = mockMvc.perform(post("/api/employee/leave/applications")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(typeId, leaveDate, leaveDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(application).get("id").asLong();

        mockMvc.perform(patch("/api/company-admin/leave/applications/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"Approved\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/employee/leave/applications/{id}/cancellation", requestId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Cancel\"}"))
                .andExpect(status().isOk());

        LeaveRequest request = requestRepository.findById(requestId).orElseThrow();
        request.setStartDate(LocalDate.now().minusDays(1));
        requestRepository.saveAndFlush(request);
        mockMvc.perform(patch("/api/company-admin/leave/applications/{id}/cancellation", requestId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"comment\":\"Too late\"}"))
                .andExpect(status().isConflict());
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(LeaveRequestStatus.CANCELLATION_PENDING);
        assertThat(attendanceRepository.findByLeaveRequestIdOrderByDateAsc(requestId)).hasSize(1);
        assertThat(balanceRepository.findByEmployeeIdAndCompanyIdAndTenantIdAndLeaveTypeIdAndLeaveYear(
                employeeA.getId(), companyA.getId(), companyA.getTenantId(), typeId, leaveDate.getYear())
                .orElseThrow().getPaidUsed()).isEqualByComparingTo("1.00");
    }

    @Test
    void ledgerRejectsCrossCompanyLeaveRequestForBalance() {
        LeaveType typeA = saveLeaveType(companyA, "LEAVE-A");
        LeaveType typeB = saveLeaveType(companyB, "LEAVE-B");
        LeaveBalance balance = saveLeaveBalance(employeeA, companyA, typeA);
        LeaveRequest requestB = saveLeaveRequest(employeeB, companyB, typeB);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(LeaveBalanceTransaction.builder()
                .leaveBalance(balance)
                .leaveRequest(requestB)
                .actor(companyAdmin)
                .action(LeaveLedgerAction.APPROVAL)
                .paidDelta(BigDecimal.ONE)
                .lopDelta(BigDecimal.ZERO)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ledgerRejectsRequestForDifferentEmployeeInSameCompany() {
        LeaveType type = saveLeaveType(companyA, "LEAVE-SHARED");
        LeaveBalance balance = saveLeaveBalance(employeeA, companyA, type);
        LeaveRequest otherEmployeeRequest = saveLeaveRequest(secondEmployeeA, companyA, type);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(LeaveBalanceTransaction.builder()
                .leaveBalance(balance)
                .leaveRequest(otherEmployeeRequest)
                .actor(companyAdmin)
                .action(LeaveLedgerAction.APPROVAL)
                .paidDelta(BigDecimal.ONE)
                .lopDelta(BigDecimal.ZERO)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attendanceRejectsLeaveRequestForDifferentEmployeeInSameCompany() {
        LeaveType type = saveLeaveType(companyA, "LEAVE-ATTENDANCE");
        LeaveRequest request = saveLeaveRequest(employeeA, companyA, type);
        Attendance attendance = Attendance.builder()
                .tenantId(companyA.getTenantId())
                .companyId(companyA.getId())
                .employeeId(secondEmployeeA.getId())
                .date(request.getStartDate())
                .status(AttendanceStatus.ON_LEAVE)
                .timeZoneId("UTC")
                .approvalStatus(AttendanceApprovalStatus.NOT_REQUIRED)
                .createdBy(hrAdmin.getId())
                .updatedBy(hrAdmin.getId())
                .leaveRequestId(request.getId())
                .leaveDayFraction(BigDecimal.ONE)
                .leavePaid(true)
                .build();

        assertThatThrownBy(() -> attendanceRepository.saveAndFlush(attendance))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private LeaveType saveLeaveType(Company company, String code) {
        return leaveTypeRepository.saveAndFlush(LeaveType.builder()
                .tenantId(company.getTenantId())
                .companyId(company.getId())
                .code(code)
                .name(code)
                .paid(true)
                .annualEntitlement(new BigDecimal("10.00"))
                .carryForwardLimit(BigDecimal.ZERO)
                .maxConsecutiveDays(30)
                .supportingDocumentRequired(false)
                .allowPastApplications(true)
                .allowHalfDay(true)
                .allowNegativeBalance(false)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private LeaveBalance saveLeaveBalance(Employee employee, Company company, LeaveType type) {
        return balanceRepository.saveAndFlush(LeaveBalance.builder()
                .employeeId(employee.getId())
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .leaveTypeId(type.getId())
                .leaveYear(LocalDate.now().getYear())
                .openingBalance(BigDecimal.ZERO)
                .annualEntitlement(new BigDecimal("10.00"))
                .carryForward(BigDecimal.ZERO)
                .accrued(BigDecimal.ZERO)
                .paidUsed(BigDecimal.ZERO)
                .lopUsed(BigDecimal.ZERO)
                .build());
    }

    private LeaveRequest saveLeaveRequest(Employee employee, Company company, LeaveType type) {
        LocalDate day = LocalDate.now().plusDays(10);
        return requestRepository.saveAndFlush(LeaveRequest.builder()
                .tenantId(company.getTenantId())
                .companyId(company.getId())
                .employeeId(employee.getId())
                .leaveTypeId(type.getId())
                .leaveType(type.getName())
                .startDate(day)
                .endDate(day)
                .days(BigDecimal.ONE)
                .workingDates(day.toString())
                .paid(true)
                .halfDay(false)
                .status(LeaveRequestStatus.PENDING)
                .reason("Test ledger consistency")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }
}

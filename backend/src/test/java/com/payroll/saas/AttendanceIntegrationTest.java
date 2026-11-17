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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceRuleRepository attendanceRuleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Company companyA;
    private Company companyB;
    private User superAdmin;
    private User companyAdmin;
    private User hrAdmin;
    private User employeeUser;
    private Employee employeeA;
    private Employee employeeB;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        attendanceRuleRepository.deleteAll();
        payrollRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        designationRepository.deleteAll();
        departmentRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Acme", "ACME-ATT", 31001L);
        companyB = saveCompany("Beta", "BETA-ATT", 42002L);
        superAdmin = saveUser("super", "super@attendance.test", UserRole.SUPER_ADMIN, null, 0L);
        companyAdmin = saveUser("admin-a", "admin@acme-att.test", UserRole.COMPANY_ADMIN, companyA, companyA.getTenantId());
        hrAdmin = saveUser("hr-a", "hr@acme-att.test", UserRole.HR_ADMIN, companyA, companyA.getTenantId());
        employeeUser = saveUser("employee-a", "ava@acme-att.test", UserRole.EMPLOYEE, companyA, companyA.getTenantId());
        saveUser("admin-b", "admin@beta-att.test", UserRole.COMPANY_ADMIN, companyB, companyB.getTenantId());
        employeeA = saveEmployee(companyA, "AA-001", employeeUser.getEmail());
        employeeA.setUser(employeeUser);
        employeeA = employeeRepository.saveAndFlush(employeeA);
        employeeB = saveEmployee(companyB, "BB-001", "ben@beta-att.test");
    }

    private Company saveCompany(String name, String code, Long tenantId) {
        return companyRepository.save(Company.builder()
                .name(name).code(code).email("info@" + code.toLowerCase() + ".test")
                .phone("+1000000000").address("Company Street").tenantId(tenantId)
                .subscriptionPlan("PRO").status("ACTIVE").createdAt(LocalDateTime.now()).build());
    }

    private User saveUser(String username, String email, UserRole role, Company company, Long tenantId) {
        return userRepository.save(User.builder()
                .username(username).email(email).password(passwordEncoder.encode("Safe@Test123"))
                .role(role).company(company).tenantId(tenantId).enabled(true).accountNonLocked(true)
                .createdAt(LocalDateTime.now()).build());
    }

    private Employee saveEmployee(Company company, String number, String email) {
        return employeeRepository.save(Employee.builder()
                .firstName("Ava").lastName("Adams").employeeNumber(number).email(email)
                .phone("+1555000000").address("Home Street").tenantId(company.getTenantId())
                .companyId(company.getId()).dateOfJoining(LocalDate.now().minusMonths(2))
                .employmentStatus("FULL_TIME").jobTitle("Engineer").status("ACTIVE")
                .createdAt(LocalDate.now()).build());
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

    private String manualBody(Long employeeId, LocalDate date, String status,
                              String checkIn, String checkOut) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeId", employeeId);
        request.put("date", date.toString());
        request.put("status", status);
        request.put("checkInTime", checkIn);
        request.put("checkOutTime", checkOut);
        request.put("notes", "Entered by HR");
        return objectMapper.writeValueAsString(request);
    }

    private long createManual(String token, Long employeeId, LocalDate date, String status,
                              String checkIn, String checkOut) throws Exception {
        String response = mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeId, date, status, checkIn, checkOut)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void employeeCanCheckInAndCheckOutOnce() throws Exception {
        String token = login(employeeUser);
        mockMvc.perform(post("/api/employee/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"On site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeA.getId()))
                .andExpect(jsonPath("$.checkInTime").exists());
        mockMvc.perform(post("/api/employee/attendance/check-out")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkOutTime").exists());
        assertThat(attendanceRepository.findByEmployeeIdAndDate(employeeA.getId(), LocalDate.now())
                .orElseThrow().getCheckOutTime()).isNotNull();
    }

    @Test
    void duplicateCheckInAndDailyAttendanceAreRejected() throws Exception {
        String token = login(employeeUser);
        mockMvc.perform(post("/api/employee/attendance/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/employee/attendance/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + login(hrAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeA.getId(), LocalDate.now(), "PRESENT", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkoutWithoutCheckinAndInvalidManualPunchTimesAreRejected() throws Exception {
        String employeeToken = login(employeeUser);
        mockMvc.perform(post("/api/employee/attendance/check-out")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + login(hrAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeA.getId(), LocalDate.of(2026, 11, 13),
                                "PRESENT", "10:00:00", "09:59:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCanCreateAndApproveManualAttendance() throws Exception {
        String hrToken = login(hrAdmin);
        LocalDate date = LocalDate.of(2026, 11, 13);
        long id = createManual(hrToken, employeeA.getId(), date, "PRESENT", "09:00:00", "17:00:00");
        assertThat(attendanceRepository.findById(id).orElseThrow().getCreatedBy()).isEqualTo(hrAdmin.getId());
        assertThat(attendanceRepository.findById(id).orElseThrow().getApprovalStatus())
                .isEqualTo(AttendanceApprovalStatus.PENDING);

        mockMvc.perform(patch("/api/company-admin/attendance/{id}/approval", id)
                        .header("Authorization", "Bearer " + login(companyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(companyAdmin.getId()));
        mockMvc.perform(put("/api/company-admin/attendance/{id}/correction", id)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRESENT\",\"checkInTime\":\"09:05:00\",\"checkOutTime\":\"17:00:00\",\"notes\":\"Post-approval correction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.updatedBy").value(hrAdmin.getId()));
        assertThat(attendanceRepository.findById(id).orElseThrow().getApprovedBy()).isNull();

        long rejectId = createManual(hrToken, employeeA.getId(), date.plusDays(1), "ABSENT", null, null);
        mockMvc.perform(patch("/api/company-admin/attendance/{id}/approval", rejectId)
                        .header("Authorization", "Bearer " + login(companyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.updatedBy").value(companyAdmin.getId()));
        assertThat(attendanceRepository.findById(rejectId).orElseThrow().getApprovedBy()).isNull();
    }

    @Test
    void lateArrivalWorkingMinutesAndOvertimeUseCompanyRules() throws Exception {
        String adminToken = login(hrAdmin);
        LocalDate date = LocalDate.of(2026, 11, 13);
        long id = createManual(adminToken, employeeA.getId(), date, "PRESENT", "09:16:00", "17:46:00");
        Attendance record = attendanceRepository.findById(id).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(record.isLateArrival()).isTrue();
        assertThat(record.getTotalWorkingMinutes()).isEqualTo(510);
        assertThat(record.getOvertimeMinutes()).isEqualTo(30);
    }

    @Test
    void dailyListingSupportsEmployeeSearchAndStatusFilter() throws Exception {
        String token = login(hrAdmin);
        createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null);
        mockMvc.perform(get("/api/company-admin/attendance/daily")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-11-13")
                        .param("search", "AA-001")
                        .param("status", "ABSENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNumber").value("AA-001"))
                .andExpect(jsonPath("$.content[0].status").value("ABSENT"));
        mockMvc.perform(get("/api/company-admin/attendance/reports")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-13").param("to", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeNumber").value("AA-001"))
                .andExpect(jsonPath("$[0].absentDays").value(1));
    }

    @Test
    void correctionIsAuditedAndReturnsToPendingApproval() throws Exception {
        String token = login(hrAdmin);
        long id = createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null);
        mockMvc.perform(put("/api/company-admin/attendance/{id}/correction", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRESENT\",\"checkInTime\":\"09:00:00\",\"checkOutTime\":\"17:00:00\",\"notes\":\"Corrected punch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.updatedBy").value(hrAdmin.getId()));
        assertThat(attendanceRepository.findById(id).orElseThrow().getTotalWorkingMinutes()).isEqualTo(480);
    }

    @Test
    void monthlySummaryCountsStatusesAndWorkingTotals() throws Exception {
        String token = login(hrAdmin);
        createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 2), "PRESENT", "09:00:00", "17:00:00");
        createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 3), "PRESENT", "09:20:00", "17:50:00");
        createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 4), "ABSENT", null, null);

        mockMvc.perform(get("/api/company-admin/attendance/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentDays").value(1))
                .andExpect(jsonPath("$.lateDays").value(1))
                .andExpect(jsonPath("$.absentDays").value(1))
                .andExpect(jsonPath("$.lateArrivals").value(1))
                .andExpect(jsonPath("$.totalWorkingMinutes").value(990))
                .andExpect(jsonPath("$.overtimeMinutes").value(30));
    }

    @Test
    void employeeCannotViewOrModifyOtherEmployeesAttendance() throws Exception {
        String token = login(employeeUser);
        mockMvc.perform(get("/api/company-admin/attendance/employees/{id}", employeeB.getId())
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/company-admin/attendance/{id}/correction", 1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRESENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyCannotAccessAnotherCompanyAttendanceOrCreateForItsEmployee() throws Exception {
        String token = login(hrAdmin);
        mockMvc.perform(get("/api/company-admin/attendance/employees/{id}", employeeB.getId())
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeB.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendedCompanyCannotUseAttendanceApis() throws Exception {
        String token = login(companyAdmin);
        String employeeToken = login(employeeUser);
        companyA.setStatus("SUSPENDED");
        companyRepository.saveAndFlush(companyA);
        mockMvc.perform(get("/api/company-admin/attendance/daily")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-11-13"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/employee/attendance/check-in")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCanOnlyReadOwnAttendanceHistory() throws Exception {
        String hrToken = login(hrAdmin);
        createManual(hrToken, employeeA.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null);
        mockMvc.perform(get("/api/employee/attendance/history")
                        .header("Authorization", "Bearer " + login(employeeUser))
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(employeeA.getId()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/employee/attendance/monthly")
                        .header("Authorization", "Bearer " + login(employeeUser))
                        .param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absentDays").value(1));
    }

    @Test
    void companyAttendanceRulesAreScopedAndValidated() throws Exception {
        String token = login(companyAdmin);
        String rules = """
                {
                  "workStartTime":"08:30:00",
                  "workEndTime":"17:30:00",
                  "requiredWorkingMinutes":480,
                  "gracePeriodMinutes":10,
                  "halfDayThresholdMinutes":240,
                  "overtimeThresholdMinutes":45,
                  "weeklyHolidays":["SUNDAY"],
                  "timeZoneId":"Asia/Kolkata"
                }
                """;
        mockMvc.perform(put("/api/company-admin/attendance/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZoneId").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.weeklyHolidays[0]").value("SUNDAY"));
        assertThat(attendanceRuleRepository.findByCompanyIdAndTenantId(companyA.getId(), companyA.getTenantId()))
                .isPresent();
        mockMvc.perform(get("/api/company-admin/attendance/rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workStartTime").value("08:30:00"))
                .andExpect(jsonPath("$.timeZoneId").value("Asia/Kolkata"));
        mockMvc.perform(put("/api/company-admin/attendance/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rules.replace("Asia/Kolkata", "Not/AZone")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualAttendanceUsesConfiguredTimezoneForDstTransitions() throws Exception {
        String token = login(hrAdmin);
        String rules = """
                {
                  "workStartTime":"09:00:00",
                  "workEndTime":"17:00:00",
                  "requiredWorkingMinutes":480,
                  "gracePeriodMinutes":15,
                  "halfDayThresholdMinutes":240,
                  "overtimeThresholdMinutes":30,
                  "weeklyHolidays":["SATURDAY","SUNDAY"],
                  "timeZoneId":"America/New_York"
                }
                """;
        mockMvc.perform(put("/api/company-admin/attendance/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk());

        long id = createManual(token, employeeA.getId(), LocalDate.of(2026, 11, 1),
                "PRESENT", "01:30:00", "02:30:00");
        Attendance record = attendanceRepository.findById(id).orElseThrow();
        assertThat(record.getTimeZoneId()).isEqualTo("America/New_York");
        assertThat(record.getTotalWorkingMinutes()).isEqualTo(120);

        mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeA.getId(), LocalDate.of(2026, 3, 8),
                                "PRESENT", "02:30:00", "03:30:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void employeeCannotUseManagementEndpointsAndHrCannotUseSuperAdminAttendance() throws Exception {
        mockMvc.perform(post("/api/company-admin/attendance/manual")
                        .header("Authorization", "Bearer " + login(employeeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody(employeeA.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/super-admin/attendance")
                        .header("Authorization", "Bearer " + login(hrAdmin))
                        .param("companyId", companyA.getId().toString())
                        .param("date", "2026-11-13"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanViewPlatformAttendanceWithoutTenantParametersFromUsers() throws Exception {
        createManual(login(hrAdmin), employeeA.getId(), LocalDate.of(2026, 11, 13), "ABSENT", null, null);
        mockMvc.perform(get("/api/super-admin/attendance")
                        .header("Authorization", "Bearer " + login(superAdmin))
                        .param("companyId", companyA.getId().toString())
                        .param("date", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(employeeA.getId()));
    }
}

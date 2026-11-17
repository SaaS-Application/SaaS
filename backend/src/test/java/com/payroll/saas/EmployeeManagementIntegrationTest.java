package com.payroll.saas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.LoginRequest;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Department;
import com.payroll.saas.entity.Designation;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.repository.CompanyRepository;
import com.payroll.saas.repository.AttendanceRepository;
import com.payroll.saas.repository.AttendanceRuleRepository;
import com.payroll.saas.repository.DepartmentRepository;
import com.payroll.saas.repository.DesignationRepository;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.repository.AuditLogRepository;
import com.payroll.saas.repository.PayrollRepository;
import com.payroll.saas.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
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
    private Department departmentA;
    private Department departmentB;
    private Designation designationA;
    private Designation designationB;
    private Employee employeeA;
    private Employee anotherEmployeeA;
    private Employee employeeB;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        attendanceRuleRepository.deleteAll();
        payrollRepository.deleteAll();
        auditLogRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        designationRepository.deleteAll();
        departmentRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Acme", "ACME-EMP", 11001L);
        companyB = saveCompany("Beta", "BETA-EMP", 22002L);
        superAdmin = saveUser("super", "super@payroll.test", UserRole.SUPER_ADMIN, null, 0L);
        companyAdmin = saveUser("admin-a", "admin@acme.test", UserRole.COMPANY_ADMIN, companyA, companyA.getTenantId());
        hrAdmin = saveUser("hr-a", "hr@acme.test", UserRole.HR_ADMIN, companyA, companyA.getTenantId());
        employeeUser = saveUser("employee-a", "ava@acme.test", UserRole.EMPLOYEE, companyA, companyA.getTenantId());
        saveUser("admin-b", "admin@beta.test", UserRole.COMPANY_ADMIN, companyB, companyB.getTenantId());

        departmentA = saveDepartment(companyA, "Engineering");
        departmentB = saveDepartment(companyB, "Engineering");
        designationA = saveDesignation(companyA, "Engineer");
        designationB = saveDesignation(companyB, "Engineer");
        employeeA = saveEmployee(companyA, departmentA, designationA, "AC-001", "ava@acme.test", "Ava", "Adams");
        anotherEmployeeA = saveEmployee(companyA, departmentA, designationA, "AC-002", "other@acme.test", "Owen", "Other");
        employeeB = saveEmployee(companyB, departmentB, designationB, "BE-001", "ben@beta.test", "Ben", "Beta");
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

    private Department saveDepartment(Company company, String name) {
        return departmentRepository.save(Department.builder().name(name).companyId(company.getId())
                .tenantId(company.getTenantId()).active(true).build());
    }

    private Designation saveDesignation(Company company, String name) {
        return designationRepository.save(Designation.builder().name(name).companyId(company.getId())
                .tenantId(company.getTenantId()).active(true).build());
    }

    private Employee saveEmployee(Company company, Department department, Designation designation,
                                  String number, String email, String firstName, String lastName) {
        return employeeRepository.save(Employee.builder()
                .firstName(firstName).lastName(lastName).employeeNumber(number).email(email)
                .phone("+1555000000").address("Home Street").tenantId(company.getTenantId())
                .companyId(company.getId()).department(department).designation(designation)
                .dateOfJoining(LocalDate.now().minusMonths(2)).employmentStatus("FULL_TIME")
                .jobTitle("Engineer").status("ACTIVE")
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

    private String employeePayload(String number, String email, Long departmentId, Long designationId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeNumber", number);
        request.put("firstName", "Casey");
        request.put("lastName", "Taylor");
        request.put("email", email);
        request.put("phone", "+15551112222");
        request.put("address", "New address");
        request.put("dateOfBirth", "1994-04-12");
        request.put("gender", "PREFER_NOT_TO_SAY");
        request.put("employmentType", "FULL_TIME");
        request.put("jobTitle", "Developer");
        request.put("reportingManagerId", null);
        request.put("emergencyContactName", "Alex Taylor");
        request.put("emergencyContactPhone", "+15553334444");
        request.put("emergencyContactRelationship", "Sibling");
        request.put("departmentId", departmentId);
        request.put("designationId", designationId);
        request.put("dateOfJoining", LocalDate.now().minusDays(5).toString());
        request.put("status", "ACTIVE");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void companyAdminCanCreateEmployee() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-003", "new@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.departmentName").value("Engineering"));
        assertThat(employeeRepository.existsByCompanyIdAndEmployeeNumberIgnoreCase(companyA.getId(), "AC-003")).isTrue();
    }

    @Test
    void hrAdminCanCreateEmployee() throws Exception {
        String token = login(hrAdmin);
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-004", "hr-created@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void listingSupportsSearchFiltersAndPagination() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(get("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "Ava")
                        .param("departmentId", departmentA.getId().toString())
                        .param("employmentType", "FULL_TIME")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNumber").value("AC-001"))
                .andExpect(jsonPath("$.content[0].dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.content[0].address").doesNotExist());
    }

    @Test
    void companyAdminCanUpdateEmployee() throws Exception {
        String token = login(companyAdmin);
        String payload = employeePayload("AC-001", "ava@acme.test", departmentA.getId(), designationA.getId())
                .replace("\"firstName\":\"Casey\"", "\"firstName\":\"Avery\"");
        mockMvc.perform(put("/api/company-admin/employees/{id}", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Avery"));
        assertThat(employeeRepository.findById(employeeA.getId()).orElseThrow().getFirstName()).isEqualTo("Avery");
    }

    @Test
    void rejectsDuplicateEmployeeNumberAndEmailInSameCompany() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("ac-001", "unique@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-099", "AVA@ACME.TEST", departmentA.getId(), designationA.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void companyAdminCanExplicitlyLinkMatchingEmployeeAccount() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(post("/api/company-admin/employees/{employeeId}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()))))
                .andExpect(status().isNoContent());

        assertThat(employeeRepository.findByUser_Id(employeeUser.getId()).orElseThrow().getId())
                .isEqualTo(employeeA.getId());
        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("EMPLOYEE_ACCOUNT_LINKED"));

        mockMvc.perform(get("/api/employee/me/profile")
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void hrAdminCanExplicitlyLinkEmployeeAccount() throws Exception {
        mockMvc.perform(post("/api/company-admin/employees/{employeeId}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + login(hrAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()))))
                .andExpect(status().isNoContent());
        assertThat(employeeRepository.findByUser_Id(employeeUser.getId()).orElseThrow().getId())
                .isEqualTo(employeeA.getId());
    }

    @Test
    void duplicateEmployeeAccountLinkIsRejectedAndDatabaseEnforcesOneToOne() throws Exception {
        String token = login(companyAdmin);
        String request = objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()));
        mockMvc.perform(post("/api/company-admin/employees/{id}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/company-admin/employees/{id}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        Employee secondProfile = employeeRepository.findById(anotherEmployeeA.getId()).orElseThrow();
        secondProfile.setUser(employeeUser);
        assertThatThrownBy(() -> employeeRepository.saveAndFlush(secondProfile))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountLinkingRejectsUsersFromAnotherCompany() throws Exception {
        User employeeFromCompanyB = saveUser(
                "employee-b", "ben@beta.test", UserRole.EMPLOYEE, companyB, companyB.getTenantId());
        String token = login(companyAdmin);

        mockMvc.perform(post("/api/company-admin/employees/{id}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeFromCompanyB.getId()))))
                .andExpect(status().isForbidden());

        assertThat(employeeRepository.findByUser_Id(employeeFromCompanyB.getId())).isEmpty();
    }

    @Test
    void emailMatchAloneDoesNotLinkEmployeeOrGrantSelfService() throws Exception {
        String token = login(employeeUser);

        mockMvc.perform(get("/api/employee/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Employee login is not linked to a profile; contact your company administrator"));

        mockMvc.perform(post("/api/employee/attendance/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/company-admin/employees/{employeeId}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()))))
                .andExpect(status().isForbidden());

        assertThat(employeeRepository.findByUser_Id(employeeUser.getId())).isEmpty();

        mockMvc.perform(get("/api/employee/records/{id}", anotherEmployeeA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountLinkingRejectsUserWithMismatchedTenant() throws Exception {
        employeeUser.setTenantId(99999L);
        userRepository.saveAndFlush(employeeUser);

        mockMvc.perform(post("/api/company-admin/employees/{employeeId}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + login(companyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()))))
                .andExpect(status().isForbidden());

        assertThat(employeeRepository.findByUser_Id(employeeUser.getId())).isEmpty();
    }

    @Test
    void accountLinkingRejectsInactiveEmployeeAccounts() throws Exception {
        employeeUser.setEnabled(false);
        userRepository.saveAndFlush(employeeUser);

        mockMvc.perform(post("/api/company-admin/employees/{employeeId}/account", employeeA.getId())
                        .header("Authorization", "Bearer " + login(companyAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", employeeUser.getId()))))
                .andExpect(status().isForbidden());

        assertThat(employeeRepository.findByUser_Id(employeeUser.getId())).isEmpty();
    }

    @Test
    void companyAdminCanActivateAndDeactivateEmployee() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(patch("/api/company-admin/employees/{id}/status", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(patch("/api/company-admin/employees/{id}/status", employeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void companyCannotReadOrUpdateOtherCompanyEmployee() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(get("/api/company-admin/employees/{id}", employeeB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/company-admin/employees/{id}", employeeB.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("BE-001", "ben@beta.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCrossTenantDepartmentAndDesignationAssignment() throws Exception {
        String token = login(companyAdmin);
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-005", "cross-dept@acme.test", departmentB.getId(), designationA.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-006", "cross-desig@acme.test", departmentA.getId(), designationB.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotReadOtherEmployeesOrModifyProfiles() throws Exception {
        String token = login(employeeUser);
        employeeA.setUser(employeeUser);
        employeeRepository.saveAndFlush(employeeA);
        mockMvc.perform(get("/api/employee/records/{id}", anotherEmployeeA.getId())
                        .param("companyId", companyA.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/company-admin/employees/{id}", anotherEmployeeA.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-002", "other@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employee/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber").value("AC-001"));
    }

    @Test
    void employeeCannotAccessHrManagementAndUnauthorizedRolesCannotCreate() throws Exception {
        String token = login(employeeUser);
        mockMvc.perform(get("/api/company-admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-010", "blocked@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + login(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("AC-011", "blocked2@acme.test", departmentA.getId(), designationA.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendedCompanyCannotUseEmployeeManagementEndpoints() throws Exception {
        String token = login(companyAdmin);
        companyA.setStatus("SUSPENDED");
        companyRepository.saveAndFlush(companyA);
        mockMvc.perform(get("/api/company-admin/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentAndDesignationCrudAreCompanyScoped() throws Exception {
        String token = login(hrAdmin);
        String unitBody = "{\"name\":\"Operations\"}";
        String departmentResponse = mockMvc.perform(post("/api/company-admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(unitBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long newDepartmentId = objectMapper.readTree(departmentResponse).get("id").asLong();

        mockMvc.perform(put("/api/company-admin/departments/{id}", newDepartmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Business Operations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Business Operations"));
        mockMvc.perform(patch("/api/company-admin/departments/{id}/status", newDepartmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/company-admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + newDepartmentId + ")].active").value(false));
        mockMvc.perform(post("/api/company-admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"engineering\"}"))
                .andExpect(status().isConflict());

        String designationResponse = mockMvc.perform(post("/api/company-admin/designations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(unitBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long designationId = objectMapper.readTree(designationResponse).get("id").asLong();
        mockMvc.perform(put("/api/company-admin/designations/{id}", designationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations Lead\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Operations Lead"));
        mockMvc.perform(patch("/api/company-admin/designations/{id}/status", designationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/company-admin/designations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + designationId + ")].name").value("Operations Lead"));
        mockMvc.perform(put("/api/company-admin/departments/{id}", departmentB.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanReadAuthorizedPlatformWideEmployeeResources() throws Exception {
        String token = login(superAdmin);
        mockMvc.perform(get("/api/super-admin/companies/{companyId}/employees", companyB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNumber").value("BE-001"));
        mockMvc.perform(get("/api/super-admin/companies/{companyId}/employees/{employeeId}", companyB.getId(), employeeB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ben@beta.test"))
                .andExpect(jsonPath("$.emergencyContactPhone").doesNotExist());
    }
}

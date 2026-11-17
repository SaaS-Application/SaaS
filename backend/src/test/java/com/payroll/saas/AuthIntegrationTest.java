package com.payroll.saas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.LoginRequest;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.Payroll;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.CompanyRepository;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.repository.PayrollRepository;
import com.payroll.saas.repository.UserRepository;
import com.payroll.saas.service.AuthService;
import com.payroll.saas.service.PayrollService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private PayrollService payrollService;

    private Company companyA;
    private Company companyB;
    private User superAdmin;
    private User companyAAdmin;
    private User companyAhr;
    private User companyAEmployee;
    private User companyBAdmin;
    private User companyBEmployee;
    private Employee employeeA;
    private Employee employeeB;

    @BeforeEach
    void setUp() {
        payrollRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(Company.builder()
                .name("Acme Corp")
                .code("ACM-001")
                .email("info@acme.test")
                .phone("+1000000001")
                .address("Acme Avenue")
                .tenantId(1001L)
                .subscriptionPlan("PRO")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());

        companyB = companyRepository.save(Company.builder()
                .name("Beta Labs")
                .code("BET-002")
                .email("info@beta.test")
                .phone("+1000000002")
                .address("Beta Avenue")
                .tenantId(2002L)
                .subscriptionPlan("PRO")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());

        superAdmin = saveUser("superadmin", "superadmin@payroll.local", "Test@12345", UserRole.SUPER_ADMIN, 0L, null, null);
        companyAAdmin = saveUser("acme-admin", "acme-admin@acme.test", "StrongPass@123", UserRole.COMPANY_ADMIN, companyA.getTenantId(), companyA.getId(), companyA);
        companyAhr = saveUser("acme-hr", "acme-hr@acme.test", "StrongPass@123", UserRole.HR_ADMIN, companyA.getTenantId(), companyA.getId(), companyA);
        companyAEmployee = saveUser("acme-emp", "acme-emp@acme.test", "StrongPass@123", UserRole.EMPLOYEE, companyA.getTenantId(), companyA.getId(), companyA);
        companyBAdmin = saveUser("beta-admin", "beta-admin@beta.test", "StrongPass@123", UserRole.COMPANY_ADMIN, companyB.getTenantId(), companyB.getId(), companyB);
        companyBEmployee = saveUser("beta-emp", "beta-emp@beta.test", "StrongPass@123", UserRole.EMPLOYEE, companyB.getTenantId(), companyB.getId(), companyB);

        employeeA = employeeRepository.save(Employee.builder()
                .firstName("Ava")
                .lastName("Alpha")
                .employeeNumber("EMP-1001")
                .email(companyAEmployee.getEmail())
                .phone("+1555000001")
                .address("A Street")
                .jobTitle("Analyst")
                .tenantId(companyA.getTenantId())
                .companyId(companyA.getId())
                .dateOfJoining(LocalDate.now().minusYears(1))
                .employmentStatus("FULL_TIME")
                .status("ACTIVE")
                .createdAt(LocalDate.now())
                .build());

        employeeB = employeeRepository.save(Employee.builder()
                .firstName("Ben")
                .lastName("Bravo")
                .employeeNumber("EMP-2002")
                .email(companyBEmployee.getEmail())
                .phone("+1555000002")
                .address("B Street")
                .jobTitle("Analyst")
                .tenantId(companyB.getTenantId())
                .companyId(companyB.getId())
                .dateOfJoining(LocalDate.now().minusYears(2))
                .employmentStatus("FULL_TIME")
                .status("ACTIVE")
                .createdAt(LocalDate.now())
                .build());

        payrollRepository.save(Payroll.builder()
                .tenantId(companyA.getTenantId())
                .companyId(companyA.getId())
                .employeeId(employeeA.getId())
                .payPeriod("2026-11")
                .grossSalary(new BigDecimal("5000.00"))
                .deductions(new BigDecimal("250.00"))
                .netSalary(new BigDecimal("4750.00"))
                .status("APPROVED")
                .createdAt(LocalDateTime.now())
                .build());

        payrollRepository.save(Payroll.builder()
                .tenantId(companyB.getTenantId())
                .companyId(companyB.getId())
                .employeeId(employeeB.getId())
                .payPeriod("2026-11")
                .grossSalary(new BigDecimal("6000.00"))
                .deductions(new BigDecimal("300.00"))
                .netSalary(new BigDecimal("5700.00"))
                .status("APPROVED")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private User saveUser(String username, String email, String password, UserRole role, Long tenantId, Long companyId, Company company) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .tenantId(tenantId)
                .company(company)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }

    private String loginAs(User user) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword("StrongPass@123");
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            loginRequest.setPassword("Test@12345");
        }

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("superadmin@payroll.local");
        loginRequest.setPassword("Test@12345");

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("token");
    }

    @Test
    void shouldBootstrapSuperAdminWithGlobalTenantZero() {
        userRepository.delete(superAdmin);
        userRepository.flush();

        authService.bootstrapSuperAdmin();

        User bootstrapped = userRepository.findByRole(UserRole.SUPER_ADMIN).orElseThrow();
        assertThat(bootstrapped.getTenantId()).isZero();
        assertThat(bootstrapped.getCompany()).isNull();
    }

    @Test
    void shouldRejectSuperAdminWithoutTenant() {
        User invalidSuperAdmin = User.builder()
                .username("super-no-tenant")
                .email("super-no-tenant@payroll.test")
                .password(passwordEncoder.encode("StrongPass@123"))
                .role(UserRole.SUPER_ADMIN)
                .createdAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(invalidSuperAdmin))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectSuperAdminWithNonzeroTenant() {
        User invalidSuperAdmin = User.builder()
                .username("super-wrong-tenant")
                .email("super-wrong-tenant@payroll.test")
                .password(passwordEncoder.encode("StrongPass@123"))
                .role(UserRole.SUPER_ADMIN)
                .tenantId(companyA.getTenantId())
                .createdAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(invalidSuperAdmin))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicatePayrollForEmployeeAndPayPeriod() {
        Payroll duplicate = Payroll.builder()
                .tenantId(companyA.getTenantId())
                .companyId(companyA.getId())
                .employeeId(employeeA.getId())
                .payPeriod("2026-11")
                .grossSalary(new BigDecimal("5000.00"))
                .deductions(new BigDecimal("250.00"))
                .netSalary(new BigDecimal("4750.00"))
                .status("APPROVED")
                .createdAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> payrollService.validatePayrollPeriodAvailable(duplicate))
                .isInstanceOf(ApiException.class)
                .hasMessage("A payroll record already exists for this employee and pay period");
        assertThatThrownBy(() -> payrollRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("superadmin@payroll.local");
        loginRequest.setPassword("WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAllowSuperAdminDashboard() throws Exception {
        String token = loginAs(superAdmin);

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompanies").exists());
    }

    @Test
    void shouldRegisterCompany() throws Exception {
        String token = loginAs(superAdmin);

        String payload = """
                {
                  "companyName": "Gamma Motors",
                  "companyCode": "GAM-009",
                  "companyEmail": "admin@gamma.test",
                  "companyPhone": "+1234567890",
                  "companyAddress": "Gamma Street",
                  "subscriptionPlan": "PRO",
                  "adminUsername": "gammaadmin",
                  "adminPassword": "StrongPass@123"
                }
                """;

        mockMvc.perform(post("/api/super-admin/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void shouldRejectCompanyAAccessToCompanyBEmployees() throws Exception {
        String token = loginAs(companyAAdmin);

        mockMvc.perform(get("/api/company-admin/employees")
                        .param("companyId", String.valueOf(companyB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCompanyAUpdateToCompanyBEmployee() throws Exception {
        String token = loginAs(companyAAdmin);

        String payload = """
                {
                  "firstName": "Changed",
                  "lastName": "Bravo",
                  "employeeNumber": "EMP-2002",
                  "email": "beta-emp@beta.test",
                  "phone": "+1555000099",
                  "address": "Blocked Avenue",
                  "employmentType": "FULL_TIME",
                  "jobTitle": "Analyst",
                  "departmentId": 1,
                  "designationId": 1,
                  "dateOfJoining": "2026-01-01",
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(put("/api/company-admin/employees/{employeeId}", employeeB.getId())
                        .param("companyId", String.valueOf(companyB.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCompanyAAccessToCompanyBPayrollRecords() throws Exception {
        String token = loginAs(companyAAdmin);

        mockMvc.perform(get("/api/company-admin/payrolls")
                        .param("companyId", String.valueOf(companyB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectEmployeeAccessToAnotherEmployeeRecord() throws Exception {
        String token = loginAs(companyAEmployee);

        mockMvc.perform(get("/api/employee/records/{employeeId}", employeeB.getId())
                        .param("companyId", String.valueOf(companyB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectHrAdminFromSuperAdminApis() throws Exception {
        String token = loginAs(companyAhr);

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCompanyAdminAccessToOtherCompanySettings() throws Exception {
        String token = loginAs(companyAAdmin);

        mockMvc.perform(get("/api/company-admin/settings")
                        .param("companyId", String.valueOf(companyB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectSuspendedCompanyUsersEvenWithExistingToken() throws Exception {
        String token = loginAs(companyAAdmin);
        companyA.setStatus("SUSPENDED");
        companyRepository.save(companyA);

        mockMvc.perform(get("/api/company-admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectNonSuperAdminFromBootstrapEndpoint() throws Exception {
        String token = loginAs(companyAAdmin);

        mockMvc.perform(post("/api/auth/bootstrap-super-admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldIgnoreClientSuppliedTenantIdManipulation() throws Exception {
        String token = loginAs(companyAAdmin);

        mockMvc.perform(get("/api/company-admin/employees")
                        .param("companyId", String.valueOf(companyB.getId()))
                        .param("tenantId", "9999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowSuperAdminToAccessPlatformResources() throws Exception {
        String token = loginAs(superAdmin);

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompanies").exists());
    }
}

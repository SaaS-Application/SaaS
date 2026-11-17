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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalaryManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryComponentDefinitionRepository componentRepository;
    @Autowired private SalaryStructureRepository structureRepository;
    @Autowired private SalaryStructureComponentRepository structureComponentRepository;
    @Autowired private SalaryAssignmentRepository assignmentRepository;
    @Autowired private SalaryAssignmentComponentRepository assignmentComponentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Company companyA;
    private Company companyB;
    private User superAdmin;
    private User companyAdmin;
    private User hrAdmin;
    private User companyBAdmin;
    private User employeeUser;
    private User companyBEmployeeUser;
    private Employee employeeA;

    @BeforeEach
    void setUp() {
        assignmentComponentRepository.deleteAll();
        assignmentRepository.deleteAll();
        structureComponentRepository.deleteAll();
        structureRepository.deleteAll();
        componentRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = saveCompany("Acme Salary", "ACME-SAL", 61001L);
        companyB = saveCompany("Beta Salary", "BETA-SAL", 62002L);
        superAdmin = saveUser("super-sal", "super@salary.test", UserRole.SUPER_ADMIN, null, 0L);
        companyAdmin = saveUser("admin-sal", "admin@acme-sal.test", UserRole.COMPANY_ADMIN, companyA, companyA.getTenantId());
        hrAdmin = saveUser("hr-sal", "hr@acme-sal.test", UserRole.HR_ADMIN, companyA, companyA.getTenantId());
        companyBAdmin = saveUser("admin-b-sal", "admin@beta-sal.test", UserRole.COMPANY_ADMIN, companyB, companyB.getTenantId());
        employeeUser = saveUser("employee-sal", "employee@acme-sal.test", UserRole.EMPLOYEE, companyA, companyA.getTenantId());
        companyBEmployeeUser = saveUser("employee-b-sal", "employee@beta-sal.test", UserRole.EMPLOYEE, companyB, companyB.getTenantId());
        employeeA = saveEmployee(companyA, "SAL-001", employeeUser);
        saveEmployee(companyB, "SAL-B-001", companyBEmployeeUser);
        auditLogRepository.deleteAll();
    }

    private Company saveCompany(String name, String code, Long tenantId) {
        return companyRepository.save(Company.builder().name(name).code(code)
                .email("info@" + code.toLowerCase() + ".test").phone("+1000000000")
                .address("Company Street").tenantId(tenantId).subscriptionPlan("PRO")
                .status("ACTIVE").createdAt(LocalDateTime.now()).build());
    }

    private User saveUser(String username, String email, UserRole role, Company company, Long tenantId) {
        return userRepository.save(User.builder().username(username).email(email)
                .password(passwordEncoder.encode("Safe@Test123")).role(role).company(company)
                .tenantId(tenantId).enabled(true).accountNonLocked(true).createdAt(LocalDateTime.now()).build());
    }

    private Employee saveEmployee(Company company, String number, User user) {
        Employee employee = Employee.builder().firstName("Sam").lastName("Salary")
                .employeeNumber(number).email(user.getEmail()).phone("+1555000000").address("Home")
                .tenantId(company.getTenantId()).companyId(company.getId()).user(user)
                .dateOfJoining(LocalDate.now().minusMonths(2)).employmentStatus("FULL_TIME")
                .jobTitle("Engineer").status("ACTIVE").createdAt(LocalDate.now()).build();
        return employeeRepository.save(employee);
    }

    private String login(User user) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("Safe@Test123");
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createComponent(String token, String code, String type, String method) throws Exception {
        String body = """
                {"code":"%s","name":"%s","type":"%s","calculationMethod":"%s",
                 "taxable":true,"effectiveFrom":"2020-01-01"}
                """.formatted(code, code, type, method);
        String response = mockMvc.perform(post("/api/company-admin/salary/components")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createBasicStructure(String token, long basicComponentId) throws Exception {
        String body = """
                {"code":"BASE-STRUCT","name":"Base structure","effectiveFrom":"2020-01-01",
                 "components":[{"componentId":%d,"value":4000}]}
                """.formatted(basicComponentId);
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String assignmentBody(long structureId, LocalDate from) throws Exception {
        return objectMapper.writeValueAsString(Map.of("employeeId", employeeA.getId(),
                "salaryStructureId", structureId, "effectiveFrom", from.toString()));
    }

    @Test
    void calculatesFixedAndPercentageComponentsAndSnapshotsAssignedHistory() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "BASIC", "EARNING", "FIXED_AMOUNT");
        long housing = createComponent(token, "HOUSING", "EARNING", "PERCENTAGE");
        long deduction = createComponent(token, "CUSTOM-DEDUCT", "DEDUCTION", "PERCENTAGE");
        String structureBody = """
                {"code":"ENGINEER","name":"Engineer salary","effectiveFrom":"2020-01-01",
                 "components":[
                   {"componentId":%d,"value":4000},
                   {"componentId":%d,"value":10,"percentageBaseComponentId":%d},
                   {"componentId":%d,"value":5,"percentageBaseComponentId":%d}
                 ]}
                """.formatted(basic, housing, basic, deduction, basic);
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(structureBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossEarnings").value(4400.00))
                .andExpect(jsonPath("$.totalDeductions").value(200.00))
                .andExpect(jsonPath("$.illustrativeNet").value(4200.00))
                .andExpect(jsonPath("$.components.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long structureId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.structureCode").value("ENGINEER"))
                .andExpect(jsonPath("$.components.length()").value(3));

        mockMvc.perform(get("/api/company-admin/salary/employees/{id}/assignments", employeeA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].illustrativeNet").value(4200.00));
        assertThat(auditLogRepository.findAll()).anyMatch(log -> "SALARY_ASSIGNED".equals(log.getAction()));
        assertThat(assignmentComponentRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPercentageBasesAndOverlappingBackdatedAssignments() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "BASIC", "EARNING", "FIXED_AMOUNT");
        long deduction = createComponent(token, "DEDUCTION", "DEDUCTION", "FIXED_AMOUNT");
        String invalidStructure = """
                {"code":"INVALID","name":"Invalid","effectiveFrom":"2020-01-01",
                 "components":[{"componentId":%d,"value":100},
                               {"componentId":%d,"value":5,"percentageBaseComponentId":%d}]}
                """.formatted(basic, deduction, basic);
        mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(invalidStructure))
                .andExpect(status().isBadRequest());

        long structureId = createBasicStructure(token, basic);
        mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.of(2025, 1, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.of(2024, 1, 1))))
                .andExpect(status().isConflict());
        assertThat(assignmentRepository.count()).isEqualTo(1);
    }

    @Test
    void roundsPercentageAmountsToTwoDecimalsAndRejectsExcessFixedPrecision() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "BASIC", "EARNING", "FIXED_AMOUNT");
        long allowance = createComponent(token, "ALLOWANCE", "EARNING", "PERCENTAGE");
        String structureBody = """
                {"code":"ROUNDING","name":"Rounding structure","effectiveFrom":"2020-01-01",
                 "components":[{"componentId":%d,"value":10},
                               {"componentId":%d,"value":12.3456,"percentageBaseComponentId":%d}]}
                """.formatted(basic, allowance, basic);
        mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(structureBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.components[1].calculatedAmount").value(1.23))
                .andExpect(jsonPath("$.grossEarnings").value(11.23));

        String invalidPrecision = """
                {"code":"PRECISION","name":"Precision structure","effectiveFrom":"2020-01-01",
                 "components":[{"componentId":%d,"value":10.001}]}
                """.formatted(basic);
        mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPrecision))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roundsHalfCentUpAndCalculatesLargeSalaryValuesWithoutOverflow() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "LARGE-BASIC", "EARNING", "FIXED_AMOUNT");
        long housing = createComponent(token, "LARGE-HOUSING", "EARNING", "PERCENTAGE");
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"LARGE-STRUCT","name":"Large salary","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":12.3456,"percentageBaseComponentId":%d}
                                 ]}
                                """.formatted(basic, housing, basic)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.components[1].calculatedAmount").value(1234560000.00))
                .andExpect(jsonPath("$.grossEarnings").value(11234559999.99))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).get("grossEarnings").decimalValue())
                .isEqualByComparingTo("11234559999.99");

        long roundingBasic = createComponent(token, "ROUND-BASIC", "EARNING", "FIXED_AMOUNT");
        long roundingAllowance = createComponent(token, "ROUND-ALLOWANCE", "EARNING", "PERCENTAGE");
        mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"HALF-CENT","name":"Half-cent rounding","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":10.00},
                                   {"componentId":%d,"value":12.35,"percentageBaseComponentId":%d}
                                 ]}
                                """.formatted(roundingBasic, roundingAllowance, roundingBasic)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.components[1].calculatedAmount").value(1.24))
                .andExpect(jsonPath("$.grossEarnings").value(11.24));
    }

    @Test
    void acceptsMaximumMonetaryAmountAndPersistsAssignment() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "MAX-BASIC", "EARNING", "FIXED_AMOUNT");
        long percentage = createComponent(token, "MAX-PERCENT", "EARNING", "PERCENTAGE");
        long adjustment = createComponent(token, "MAX-ADJUST", "EARNING", "FIXED_AMOUNT");
        String structureBody = """
                {"code":"MAX-MONEY","name":"Maximum supported salary","effectiveFrom":"2020-01-01",
                 "components":[
                   {"componentId":%d,"value":9999999999.99},
                   {"componentId":%d,"value":9900,"percentageBaseComponentId":%d},
                   {"componentId":%d,"value":0.99}
                 ]}
                """.formatted(basic, percentage, basic, adjustment);
        String structureResponse = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(structureBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long structureId = objectMapper.readTree(structureResponse).get("id").asLong();
        assertThat(objectMapper.readTree(structureResponse).get("grossEarnings").decimalValue())
                .isEqualByComparingTo("999999999999.99");

        String assignmentResponse = mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(assignmentResponse).get("grossEarnings").decimalValue())
                .isEqualByComparingTo("999999999999.99");
        assertThat(assignmentRepository.count()).isEqualTo(1);
        assertThat(assignmentComponentRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsPercentageComponentAmountBeyondMonetaryRangeBeforePersistence() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "OVER-BASIC", "EARNING", "FIXED_AMOUNT");
        long percentage = createComponent(token, "OVER-PERCENT", "EARNING", "PERCENTAGE");
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OVER-COMPONENT","name":"Oversized percentage","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":10000.0001,"percentageBaseComponentId":%d}
                                 ]}
                                """.formatted(basic, percentage, basic)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("message").asText())
                .contains("Calculated salary component amount");
        assertThat(structureRepository.count()).isZero();
        assertThat(structureComponentRepository.count()).isZero();
    }

    @Test
    void rejectsOversizedComponentBeforePersistingSalaryAssignment() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "ASSIGN-BASIC", "EARNING", "FIXED_AMOUNT");
        long percentage = createComponent(token, "ASSIGN-PERCENT", "EARNING", "PERCENTAGE");
        String structureResponse = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ASSIGN-OVER","name":"Assignment overflow","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":9900,"percentageBaseComponentId":%d}
                                 ]}
                                """.formatted(basic, percentage, basic)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long structureId = objectMapper.readTree(structureResponse).get("id").asLong();
        SalaryStructureComponent percentageLine = structureComponentRepository
                .findBySalaryStructureIdAndCompanyIdAndTenantIdOrderById(
                        structureId, companyA.getId(), companyA.getTenantId())
                .stream().filter(line -> line.getComponentId().equals(percentage))
                .findFirst().orElseThrow();
        percentageLine.setValue(new java.math.BigDecimal("10000.0001"));
        structureComponentRepository.saveAndFlush(percentageLine);

        String response = mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).get("message").asText())
                .contains("Calculated salary component amount");
        assertThat(assignmentRepository.count()).isZero();
        assertThat(assignmentComponentRepository.count()).isZero();
    }

    @Test
    void rejectsGrossEarningsTotalBeyondMonetaryRange() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "GROSS-BASIC", "EARNING", "FIXED_AMOUNT");
        long percentage = createComponent(token, "GROSS-PERCENT", "EARNING", "PERCENTAGE");
        long adjustment = createComponent(token, "GROSS-ADJUST", "EARNING", "FIXED_AMOUNT");
        long extra = createComponent(token, "GROSS-EXTRA", "EARNING", "FIXED_AMOUNT");
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GROSS-OVER","name":"Gross total overflow","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":9900,"percentageBaseComponentId":%d},
                                   {"componentId":%d,"value":0.99},
                                   {"componentId":%d,"value":0.01}
                                 ]}
                                """.formatted(basic, percentage, basic, adjustment, extra)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("message").asText()).contains("Gross earnings");
        assertThat(structureRepository.count()).isZero();
        assertThat(structureComponentRepository.count()).isZero();
    }

    @Test
    void rejectsTotalDeductionsBeyondMonetaryRange() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "DEDUCT-BASIC", "EARNING", "FIXED_AMOUNT");
        long percentage = createComponent(token, "DEDUCT-PERCENT", "DEDUCTION", "PERCENTAGE");
        long fixed = createComponent(token, "DEDUCT-FIXED", "DEDUCTION", "FIXED_AMOUNT");
        long extra = createComponent(token, "DEDUCT-EXTRA", "DEDUCTION", "FIXED_AMOUNT");
        String response = mockMvc.perform(post("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DEDUCT-OVER","name":"Deduction total overflow","effectiveFrom":"2020-01-01",
                                 "components":[
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":9900,"percentageBaseComponentId":%d},
                                   {"componentId":%d,"value":9999999999.99},
                                   {"componentId":%d,"value":1.00}
                                 ]}
                                """.formatted(basic, percentage, basic, fixed, extra)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("message").asText()).contains("Total deductions");
        assertThat(structureRepository.count()).isZero();
        assertThat(structureComponentRepository.count()).isZero();
    }

    @Test
    void updatesCompanyComponentAndRejectsDuplicateCompanyCode() throws Exception {
        String token = login(hrAdmin);
        long componentId = createComponent(token, "BASIC", "EARNING", "FIXED_AMOUNT");
        mockMvc.perform(post("/api/company-admin/salary/components")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Duplicate\",\"type\":\"EARNING\",\"calculationMethod\":\"FIXED_AMOUNT\",\"effectiveFrom\":\"2020-01-01\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/company-admin/salary/components/{id}", componentId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Basic adjusted\",\"type\":\"EARNING\",\"calculationMethod\":\"FIXED_AMOUNT\",\"taxable\":false,\"effectiveFrom\":\"2020-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Basic adjusted"))
                .andExpect(jsonPath("$.taxable").value(false));
        assertThat(componentRepository.findById(componentId).orElseThrow().getName()).isEqualTo("Basic adjusted");
    }

    @Test
    void scopesManagementToCompanyAndEmployeeSelfServiceToLinkedProfile() throws Exception {
        String token = login(hrAdmin);
        long basic = createComponent(token, "BASIC", "EARNING", "FIXED_AMOUNT");
        long structureId = createBasicStructure(token, basic);
        mockMvc.perform(post("/api/company-admin/salary/assignments")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody(structureId, LocalDate.now())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/company-admin/salary/structures/{id}", structureId)
                        .header("Authorization", "Bearer " + login(companyBAdmin)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/company-admin/salary/structures")
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/employee/salary/history")
                        .header("Authorization", "Bearer " + login(employeeUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].illustrativeNet").value(4000.00));
        mockMvc.perform(get("/api/employee/salary/history")
                        .header("Authorization", "Bearer " + login(companyBEmployeeUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void suspendedCompanyIsBlockedAndSuperAdminCannotManageSalary() throws Exception {
        String token = login(hrAdmin);
        companyA.setStatus("SUSPENDED");
        companyRepository.save(companyA);
        mockMvc.perform(get("/api/company-admin/salary/components")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String superToken = login(superAdmin);
        mockMvc.perform(post("/api/company-admin/salary/components")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GLOBAL\",\"name\":\"Global\",\"type\":\"EARNING\",\"calculationMethod\":\"FIXED_AMOUNT\",\"effectiveFrom\":\"2020-01-01\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/super-admin/salary/summary")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies").value(2));
    }

    @Test
    void publishesSalaryEndpointsWithBearerSecurityInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/company-admin/salary/components'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employee/salary/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/super-admin/salary/summary'].get").exists());
    }
}

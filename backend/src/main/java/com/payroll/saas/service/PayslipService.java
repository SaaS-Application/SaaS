package com.payroll.saas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.saas.dto.PayrollComponentResponse;
import com.payroll.saas.dto.PayslipSummaryResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.Payroll;
import com.payroll.saas.entity.SalaryComponentType;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.repository.PayrollRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayslipService {

    private static final List<String> FINAL_STATUSES = List.of("PROCESSED", "APPROVED");
    private static final TypeReference<List<PayrollComponentResponse>> COMPONENT_LIST_TYPE = new TypeReference<>() { };

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeIdentityService employeeIdentityService;
    private final TenantSecurityService tenantSecurityService;
    private final ObjectMapper objectMapper;
    private final PayslipPdfGenerator pdfGenerator;

    @Transactional(readOnly = true)
    public List<PayslipSummaryResponse> getEmployeePayslips(User actor, String payPeriod) {
        Employee employee = employeeIdentityService.currentEmployee(actor);
        Company company = tenantSecurityService.assertCompanyAccess(actor, employee.getCompanyId());
        assertEmployeeCompany(actor, employee, company);
        List<Payroll> payrolls = payrollRepository.findPayslipsForEmployee(
                employee.getId(), company.getId(), company.getTenantId(),
                normalizePeriod(payPeriod), FINAL_STATUSES);
        return summaries(payrolls, Map.of(employee.getId(), employee), company);
    }

    @Transactional(readOnly = true)
    public List<PayslipSummaryResponse> getCompanyPayslips(
            User actor, String payPeriod, String employeeSearch) {
        Company company = managedCompany(actor);
        String period = normalizePeriod(payPeriod);
        String search = normalizeSearch(employeeSearch);
        return summaries(payrollRepository.findPayslipsForCompany(
                company.getId(), company.getTenantId(), period, search, FINAL_STATUSES), company);
    }

    @Transactional(readOnly = true)
    public List<PayslipSummaryResponse> getSuperAdminCompanyPayslips(
            User actor, Long companyId, String payPeriod, String employeeSearch) {
        requireSuperAdmin(actor);
        Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
        String period = normalizePeriod(payPeriod);
        String search = normalizeSearch(employeeSearch);
        return summaries(payrollRepository.findPayslipsForCompany(
                company.getId(), company.getTenantId(), period, search, FINAL_STATUSES), company);
    }

    @Transactional(readOnly = true)
    public PayslipFile downloadEmployeePayslip(User actor, Long payrollId) {
        Employee employee = employeeIdentityService.currentEmployee(actor);
        Company company = tenantSecurityService.assertCompanyAccess(actor, employee.getCompanyId());
        assertEmployeeCompany(actor, employee, company);
        Payroll payroll = payrollRepository.findByIdAndEmployeeIdAndCompanyIdAndTenantId(
                        payrollId, employee.getId(), company.getId(), company.getTenantId())
                .orElseThrow(this::notFound);
        return generate(payroll, employee, company);
    }

    @Transactional(readOnly = true)
    public PayslipFile downloadCompanyPayslip(User actor, Long payrollId) {
        Company company = managedCompany(actor);
        Payroll payroll = payrollRepository.findByIdAndCompanyIdAndTenantId(
                        payrollId, company.getId(), company.getTenantId())
                .orElseThrow(this::notFound);
        return generate(payroll, findEmployee(payroll), company);
    }

    @Transactional(readOnly = true)
    public PayslipFile downloadSuperAdminPayslip(User actor, Long companyId, Long payrollId) {
        requireSuperAdmin(actor);
        Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
        Payroll payroll = payrollRepository.findByIdAndCompanyIdAndTenantId(
                        payrollId, company.getId(), company.getTenantId())
                .orElseThrow(this::notFound);
        return generate(payroll, findEmployee(payroll), company);
    }

    private PayslipFile generate(Payroll payroll, Employee employee, Company company) {
        requireFinalPayroll(payroll);
        List<PayrollComponentResponse> components = readComponents(payroll);
        validateSavedTotals(payroll, components);

        boolean profileSnapshotAvailable = hasText(payroll.getCompanyNameSnapshot())
                && hasText(payroll.getCompanyAddressSnapshot())
                && hasText(payroll.getEmployeeNameSnapshot())
                && hasText(payroll.getEmployeeNumberSnapshot());
        String employeeName = firstNonBlank(payroll.getEmployeeNameSnapshot(), employeeName(employee));
        String employeeNumber = firstNonBlank(payroll.getEmployeeNumberSnapshot(), employee.getEmployeeNumber());
        String payslipNumber = firstNonBlank(payroll.getPayslipNumber(),
                legacyPayslipNumber(company.getCode(), employeeNumber, payroll.getPayPeriod()));
        LocalDate payrollDate = payroll.getProcessedOn() != null
                ? payroll.getProcessedOn()
                : payroll.getCreatedAt() == null ? null : payroll.getCreatedAt().toLocalDate();
        if (payrollDate == null) {
            throw unavailable("Payroll date is missing from this historical record");
        }

        PayslipPdfGenerator.PayslipData data = new PayslipPdfGenerator.PayslipData(
                firstNonBlank(payroll.getCompanyNameSnapshot(), company.getName()),
                firstNonBlank(payroll.getCompanyAddressSnapshot(), company.getAddress()),
                employeeName,
                employeeNumber,
                firstNonBlank(payroll.getDepartmentNameSnapshot(),
                        employee.getDepartment() == null ? null : employee.getDepartment().getName()),
                firstNonBlank(payroll.getDesignationNameSnapshot(),
                        employee.getDesignation() == null ? employee.getJobTitle() : employee.getDesignation().getName()),
                payroll.getPayPeriod(),
                payrollDate,
                payslipNumber,
                payroll.getTotalWorkingDays(),
                payroll.getPaidDays(),
                payroll.getUnpaidDays(),
                payroll.getGrossSalary(),
                payroll.getDeductions(),
                payroll.getNetSalary(),
                components,
                profileSnapshotAvailable);

        String filename = "payslip-" + payroll.getPayPeriod() + "-" + safeFilePart(payslipNumber) + ".pdf";
        return new PayslipFile(filename, pdfGenerator.generate(data));
    }

    private List<PayslipSummaryResponse> summaries(List<Payroll> payrolls, Company company) {
        if (payrolls.isEmpty()) return List.of();
        Map<Long, Employee> employees = employeeRepository.findAllById(
                        payrolls.stream().map(Payroll::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, employee -> employee));
        return summaries(payrolls, employees, company);
    }

    private List<PayslipSummaryResponse> summaries(
            List<Payroll> payrolls, Map<Long, Employee> employees, Company company) {
        return payrolls.stream().map(payroll -> {
            Employee employee = employees.get(payroll.getEmployeeId());
            if (employee == null || !Objects.equals(employee.getCompanyId(), company.getId())
                    || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
                throw unavailable("Payroll employee is unavailable in this company");
            }
            return new PayslipSummaryResponse(
                    payroll.getId(),
                    payroll.getPayPeriod(),
                    firstNonBlank(payroll.getEmployeeNameSnapshot(), employeeName(employee)),
                    firstNonBlank(payroll.getEmployeeNumberSnapshot(), employee.getEmployeeNumber()),
                    payroll.getGrossSalary(),
                    payroll.getDeductions(),
                    payroll.getNetSalary(),
                    payroll.getProcessedOn(),
                    hasText(payroll.getComponentBreakdownJson()),
                    hasText(payroll.getCompanyNameSnapshot())
                            && hasText(payroll.getCompanyAddressSnapshot())
                            && hasText(payroll.getEmployeeNameSnapshot())
                            && hasText(payroll.getEmployeeNumberSnapshot()));
        }).toList();
    }

    private List<PayrollComponentResponse> readComponents(Payroll payroll) {
        if (!hasText(payroll.getComponentBreakdownJson())) {
            throw unavailable("Historical payroll component snapshot is missing; salary structures will not be used to reconstruct it");
        }
        try {
            List<PayrollComponentResponse> components =
                    objectMapper.readValue(payroll.getComponentBreakdownJson(), COMPONENT_LIST_TYPE);
            if (components == null) {
                throw unavailable("Historical payroll component snapshot is invalid");
            }
            for (PayrollComponentResponse component : components) {
                if (component == null || component.type() == null || component.payableAmount() == null
                        || !hasText(component.name())) {
                    throw unavailable("Historical payroll component snapshot is incomplete");
                }
            }
            return components;
        } catch (JsonProcessingException exception) {
            throw unavailable("Historical payroll component snapshot is invalid");
        }
    }

    private void validateSavedTotals(Payroll payroll, List<PayrollComponentResponse> components) {
        BigDecimal earnings = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (PayrollComponentResponse component : components) {
            if (component.type() == SalaryComponentType.EARNING) {
                earnings = earnings.add(component.payableAmount());
            } else if (component.type() == SalaryComponentType.DEDUCTION) {
                deductions = deductions.add(component.payableAmount());
            } else {
                throw unavailable("Historical payroll component type is unsupported");
            }
        }
        if (payroll.getGrossSalary() == null || payroll.getDeductions() == null || payroll.getNetSalary() == null
                || earnings.compareTo(payroll.getGrossSalary()) != 0
                || deductions.compareTo(payroll.getDeductions()) != 0
                || payroll.getGrossSalary().subtract(payroll.getDeductions()).compareTo(payroll.getNetSalary()) != 0) {
            throw unavailable("Saved payroll totals do not match the saved component breakdown");
        }
    }

    private void requireFinalPayroll(Payroll payroll) {
        if (payroll.getStatus() == null || FINAL_STATUSES.stream()
                .noneMatch(status -> status.equalsIgnoreCase(payroll.getStatus()))) {
            throw notFound();
        }
        try {
            YearMonth parsed = YearMonth.parse(payroll.getPayPeriod());
            if (!parsed.toString().equals(payroll.getPayPeriod())) throw new DateTimeParseException("", "", 0);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw unavailable("Saved payroll period is invalid");
        }
    }

    private Employee findEmployee(Payroll payroll) {
        return employeeRepository.findByIdAndCompanyIdAndTenantId(
                        payroll.getEmployeeId(), payroll.getCompanyId(), payroll.getTenantId())
                .orElseThrow(() -> unavailable("Payroll employee is unavailable in this company"));
    }

    private Company managedCompany(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        if (actor.getCompany() == null) throw new AccessDeniedException("Company context missing");
        Company company = tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
        if (!Objects.equals(actor.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Tenant mismatch detected");
        }
        return company;
    }

    private void assertEmployeeCompany(User actor, Employee employee, Company company) {
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee payslip tenant mismatch");
        }
        tenantSecurityService.assertEmployeeAccess(actor, employee);
    }

    private void requireSuperAdmin(User actor) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) return null;
        try {
            YearMonth parsed = YearMonth.parse(period);
            if (!parsed.toString().equals(period)) throw new DateTimeParseException("", period, 0);
            return parsed.toString();
        } catch (DateTimeParseException exception) {
            throw new ApiException("Pay period must use the YYYY-MM format");
        }
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String normalized = search.trim();
        if (normalized.length() > 100) throw new ApiException("Employee search must be 100 characters or fewer");
        return normalized;
    }

    private String legacyPayslipNumber(String companyCode, String employeeNumber, String payPeriod) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((companyCode + "|" + employeeNumber + "|" + payPeriod)
                            .getBytes(StandardCharsets.UTF_8));
            return "PS-LEGACY-" + HexFormat.of().withUpperCase().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String employeeName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found");
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

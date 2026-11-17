package com.payroll.saas.service;

import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.repository.UserRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeIdentityService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Employee currentEmployee(User actor) {
        requireEmployee(actor);
        User currentAccount = userRepository.findById(actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Employee login account no longer exists"));
        requireActiveEmployeeAccount(currentAccount);
        Company company = companyFor(currentAccount);
        Employee linked = employeeRepository.findByUser_Id(currentAccount.getId()).orElse(null);
        if (linked != null) {
            assertEmployeeInCompany(linked, company);
            tenantSecurityService.assertEmployeeAccess(currentAccount, linked);
            return linked;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Employee login is not linked to a profile; contact your company administrator");
    }

    @Transactional
    public void linkAccount(User actor, Long employeeId, Long userId) {
        Company company = companyForManagementUser(actor);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));
        assertEmployeeInCompany(employee, company);
        if (employee.getUser() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has a login account");
        }

        User account = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User account not found"));
        if (account.getRole() != UserRole.EMPLOYEE || account.getCompany() == null
                || !Objects.equals(account.getCompany().getId(), company.getId())
                || !Objects.equals(account.getTenantId(), company.getTenantId())
                || !account.isEnabled() || !account.isAccountNonLocked()) {
            throw new AccessDeniedException("Login account must be an employee in your company and tenant");
        }
        if (!sameEmail(employee.getEmail(), account.getEmail())) {
            throw new ApiException("Employee and login account emails must match when linking");
        }
        if (employeeRepository.existsByUser_Id(account.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login account is already linked to an employee");
        }

        employee.setUser(account);
        try {
            employeeRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee account link already exists");
        }
        auditLogService.record("EMPLOYEE_ACCOUNT_LINKED", actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(),
                "Employee profile " + employee.getId() + " linked to login account " + account.getId());
    }

    private Company companyFor(User actor) {
        if (actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }
        Company company = tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
        if (!Objects.equals(actor.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Tenant mismatch detected");
        }
        return company;
    }

    private Company companyForManagementUser(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        return companyFor(actor);
    }

    private void assertEmployeeInCompany(Employee employee, Company company) {
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee does not belong to your company");
        }
    }

    private void requireEmployee(User actor) {
        if (actor == null || actor.getRole() != UserRole.EMPLOYEE) {
            throw new AccessDeniedException("Employee role required");
        }
    }

    private void requireActiveEmployeeAccount(User account) {
        if (account.getRole() != UserRole.EMPLOYEE || !account.isEnabled() || !account.isAccountNonLocked()) {
            throw new AccessDeniedException("Active employee login required");
        }
    }

    private boolean sameEmail(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }
}

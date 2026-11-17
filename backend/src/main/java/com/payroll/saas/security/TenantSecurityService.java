package com.payroll.saas.security;

import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.Payroll;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TenantSecurityService {

    private final CompanyRepository companyRepository;

    public Company assertCompanyAccess(User actor, Long companyId) {
        if (actor == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return companyRepository.findById(companyId)
                    .orElseThrow(() -> new ApiException("Company not found"));
        }

        if (companyId == null) {
            throw new ApiException("Company id is required");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException("Company not found"));

        if ("SUSPENDED".equalsIgnoreCase(company.getStatus())) {
            throw new AccessDeniedException("Company account is suspended");
        }

        if (actor.getCompany() == null || !Objects.equals(actor.getCompany().getId(), companyId)) {
            throw new AccessDeniedException("Cross-company access denied");
        }

        if (!Objects.equals(actor.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Tenant mismatch detected");
        }

        return company;
    }

    public void assertCompanyActive(User actor) {
        if (actor == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }

        if (actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }

        Company company = actor.getCompany();
        if (company == null || "SUSPENDED".equalsIgnoreCase(company.getStatus())) {
            throw new AccessDeniedException("Company account is suspended");
        }
    }

    public void assertEmployeeAccess(User actor, Employee employee) {
        if (actor == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }

        if (employee == null) {
            throw new ApiException("Employee not found");
        }

        if (!Objects.equals(employee.getTenantId(), actor.getTenantId())) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }

        if (actor.getCompany() == null || !Objects.equals(employee.getCompanyId(), actor.getCompany().getId())) {
            throw new AccessDeniedException("Cross-company employee access denied");
        }

        if (actor.getRole() == UserRole.EMPLOYEE
                && (employee.getUser() == null || !Objects.equals(employee.getUser().getId(), actor.getId()))) {
            throw new AccessDeniedException("Employees may only access their linked employee profile");
        }
    }

    public void assertPayrollAccess(User actor, Payroll payroll) {
        if (actor == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }

        if (payroll == null) {
            throw new ApiException("Payroll record not found");
        }

        if (!Objects.equals(payroll.getTenantId(), actor.getTenantId())) {
            throw new AccessDeniedException("Cross-tenant payroll access denied");
        }

        if (actor.getCompany() == null || !Objects.equals(payroll.getCompanyId(), actor.getCompany().getId())) {
            throw new AccessDeniedException("Cross-company payroll access denied");
        }

        if (actor.getRole() == UserRole.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot access payroll records");
        }
    }
}

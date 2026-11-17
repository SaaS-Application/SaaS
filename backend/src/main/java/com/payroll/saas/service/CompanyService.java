package com.payroll.saas.service;

import com.payroll.saas.dto.CompanyRegistrationRequest;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.CompanyRepository;
import com.payroll.saas.repository.UserRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final TenantSecurityService tenantSecurityService;

    @Transactional
    public Company registerCompany(CompanyRegistrationRequest request) {
        if (companyRepository.findByCode(request.getCompanyCode()).isPresent()) {
            throw new ApiException("Company code already exists");
        }

        if (userRepository.findByEmailIgnoreCase(request.getCompanyEmail()).isPresent()) {
            throw new ApiException("A company already exists with this email");
        }

        Long tenantId = System.currentTimeMillis();

        Company company = Company.builder()
                .name(request.getCompanyName())
                .code(request.getCompanyCode())
                .email(request.getCompanyEmail())
                .phone(request.getCompanyPhone())
                .address(request.getCompanyAddress())
                .tenantId(tenantId)
                .subscriptionPlan(request.getSubscriptionPlan())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        Company savedCompany = companyRepository.save(company);

        User adminUser = User.builder()
                .username(request.getAdminUsername())
                .email(request.getCompanyEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .role(UserRole.COMPANY_ADMIN)
                .tenantId(tenantId)
                .company(savedCompany)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(adminUser);
        auditLogService.record("COMPANY_REGISTERED", adminUser.getUsername(), UserRole.COMPANY_ADMIN.name(), tenantId, savedCompany.getId(),
                "Company registered with generated tenant id: " + tenantId);
        return savedCompany;
    }

    @Transactional(readOnly = true)
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    @Transactional
    public Company updateCompanyStatus(Long companyId, String status, User actor) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException("Company not found"));

        if (!"ACTIVE".equalsIgnoreCase(status) && !"SUSPENDED".equalsIgnoreCase(status)) {
            throw new ApiException("Invalid company status");
        }

        company.setStatus(status.toUpperCase());
        companyRepository.save(company);
        auditLogService.record("COMPANY_STATUS_UPDATED", actor.getUsername(), actor.getRole().name(), company.getTenantId(), company.getId(),
                "Status set to " + status.toUpperCase());
        return company;
    }

    @Transactional(readOnly = true)
    public List<User> getCompanyAdmins(Long companyId) {
        return userRepository.findByCompanyId(companyId)
                .stream()
                .filter(user -> user.getRole() == UserRole.COMPANY_ADMIN || user.getRole() == UserRole.HR_ADMIN)
                .toList();
    }

    public Company validateCompanyOwnership(User currentUser, Long companyId) {
        return tenantSecurityService.assertCompanyAccess(currentUser, companyId);
    }

    public String generateCompanyCode() {
        return "CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

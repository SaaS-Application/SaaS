package com.payroll.saas.service;

import com.payroll.saas.dto.OrganizationUnitRequest;
import com.payroll.saas.dto.OrganizationUnitResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Department;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.DepartmentRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final TenantSecurityService tenantSecurityService;

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> list(User actor) {
        Company company = company(actor);
        return departmentRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .stream().map(OrganizationUnitResponse::from).toList();
    }

    @Transactional
    public OrganizationUnitResponse create(User actor, OrganizationUnitRequest request) {
        Company company = company(actor);
        if (departmentRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), request.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department name already exists in this company");
        }
        Department department = Department.builder()
                .name(request.getName().trim())
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .active(true)
                .build();
        return OrganizationUnitResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public OrganizationUnitResponse update(User actor, Long id, OrganizationUnitRequest request) {
        Company company = company(actor);
        Department department = findOwned(id, company);
        if (departmentRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), request.getName().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department name already exists in this company");
        }
        department.setName(request.getName().trim());
        return OrganizationUnitResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public OrganizationUnitResponse setActive(User actor, Long id, boolean active) {
        Company company = company(actor);
        Department department = findOwned(id, company);
        department.setActive(active);
        return OrganizationUnitResponse.from(departmentRepository.save(department));
    }

    private Department findOwned(Long id, Company company) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException("Department not found"));
        if (!Objects.equals(department.getCompanyId(), company.getId())
                || !Objects.equals(department.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Department does not belong to your company");
        }
        return department;
    }

    private Company company(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        if (actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }
}

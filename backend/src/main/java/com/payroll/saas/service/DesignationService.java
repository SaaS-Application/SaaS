package com.payroll.saas.service;

import com.payroll.saas.dto.OrganizationUnitRequest;
import com.payroll.saas.dto.OrganizationUnitResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Designation;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.DesignationRepository;
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
public class DesignationService {
    private final DesignationRepository designationRepository;
    private final TenantSecurityService tenantSecurityService;

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> list(User actor) {
        Company company = company(actor);
        return designationRepository.findByCompanyIdAndTenantId(company.getId(), company.getTenantId())
                .stream().map(OrganizationUnitResponse::from).toList();
    }

    @Transactional
    public OrganizationUnitResponse create(User actor, OrganizationUnitRequest request) {
        Company company = company(actor);
        if (designationRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), request.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Designation name already exists in this company");
        }
        Designation designation = Designation.builder()
                .name(request.getName().trim())
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .active(true)
                .build();
        return OrganizationUnitResponse.from(designationRepository.save(designation));
    }

    @Transactional
    public OrganizationUnitResponse update(User actor, Long id, OrganizationUnitRequest request) {
        Company company = company(actor);
        Designation designation = findOwned(id, company);
        if (designationRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), request.getName().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Designation name already exists in this company");
        }
        designation.setName(request.getName().trim());
        return OrganizationUnitResponse.from(designationRepository.save(designation));
    }

    @Transactional
    public OrganizationUnitResponse setActive(User actor, Long id, boolean active) {
        Company company = company(actor);
        Designation designation = findOwned(id, company);
        designation.setActive(active);
        return OrganizationUnitResponse.from(designationRepository.save(designation));
    }

    private Designation findOwned(Long id, Company company) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Designation not found"));
        if (!Objects.equals(designation.getCompanyId(), company.getId())
                || !Objects.equals(designation.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Designation does not belong to your company");
        }
        return designation;
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

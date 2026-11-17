package com.payroll.saas.service;

import com.payroll.saas.dto.EmployeeRequest;
import com.payroll.saas.dto.EmployeeResponse;
import com.payroll.saas.dto.EmployeeSummaryResponse;
import com.payroll.saas.entity.Company;
import com.payroll.saas.entity.Department;
import com.payroll.saas.entity.Designation;
import com.payroll.saas.entity.Employee;
import com.payroll.saas.entity.User;
import com.payroll.saas.entity.UserRole;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.DepartmentRepository;
import com.payroll.saas.repository.DesignationRepository;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TenantSecurityService tenantSecurityService;
    private final EmployeeIdentityService employeeIdentityService;

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request, User actor) {
        requireManagementRole(actor);
        Company company = currentCompany(actor);
        Department department = findActiveDepartment(company, request.getDepartmentId());
        Designation designation = findActiveDesignation(company, request.getDesignationId());
        validateUniqueFields(company.getId(), request, null);
        validateJoiningDate(request);

        Employee employee = new Employee();
        employee.setTenantId(company.getTenantId());
        employee.setCompanyId(company.getId());
        applyRequest(employee, request, department, designation);
        employee.setStatus(normalize(request.getStatus()));
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeSummaryResponse> searchEmployees(
            User actor,
            String search,
            Long departmentId,
            Long designationId,
            String employmentType,
            String status,
            int page,
            int size) {
        requireManagementRole(actor);
        Company company = currentCompany(actor);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "lastName", "firstName", "id"));
        return employeeRepository.searchCompanyEmployees(
                        company.getId(),
                        company.getTenantId(),
                        emptyToNull(search),
                        departmentId,
                        designationId,
                        employmentType == null || employmentType.isBlank() ? null : normalize(employmentType),
                        status == null || status.isBlank() ? null : normalize(status),
                        pageable)
                .map(EmployeeSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeSummaryResponse> searchCompanyEmployees(
            User actor,
            Long companyId,
            String search,
            Long departmentId,
            Long designationId,
            String employmentType,
            String status,
            int page,
            int size) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
        Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "lastName", "firstName", "id"));
        return employeeRepository.searchCompanyEmployees(
                        company.getId(),
                        company.getTenantId(),
                        emptyToNull(search),
                        departmentId,
                        designationId,
                        employmentType == null || employmentType.isBlank() ? null : normalize(employmentType),
                        status == null || status.isBlank() ? null : normalize(status),
                        pageable)
                .map(EmployeeSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeSummaryResponse getCompanyEmployee(User actor, Long companyId, Long employeeId) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
        Company company = tenantSecurityService.assertCompanyAccess(actor, companyId);
        return EmployeeSummaryResponse.from(findByIdAndCompany(employeeId, company));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(User actor, Long employeeId) {
        requireManagementRole(actor);
        Company company = currentCompany(actor);
        Employee employee = findByIdAndCompany(employeeId, company);
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(User actor, Long employeeId, EmployeeRequest request) {
        requireManagementRole(actor);
        Company company = currentCompany(actor);
        Employee employee = findByIdAndCompany(employeeId, company);
        Department department = findActiveDepartment(company, request.getDepartmentId());
        Designation designation = findActiveDesignation(company, request.getDesignationId());
        validateUniqueFields(company.getId(), request, employeeId);
        validateJoiningDate(request);

        applyRequest(employee, request, department, designation);
        employee.setStatus(normalize(request.getStatus()));
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse updateStatus(User actor, Long employeeId, String status) {
        requireManagementRole(actor);
        Company company = currentCompany(actor);
        Employee employee = findByIdAndCompany(employeeId, company);
        employee.setStatus(normalize(status));
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse getOwnEmployeeProfile(User actor) {
        if (actor == null || actor.getRole() != UserRole.EMPLOYEE) {
            throw new AccessDeniedException("Only employees may access this profile endpoint");
        }
        Employee employee = employeeIdentityService.currentEmployee(actor);
        tenantSecurityService.assertEmployeeAccess(actor, employee);
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Employee profile is inactive");
        }
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse getEmployeeRecord(User actor, Long employeeId, Long requestedCompanyId) {
        Company company = requestedCompanyId == null ? currentCompany(actor) : tenantSecurityService.assertCompanyAccess(actor, requestedCompanyId);
        if (actor.getRole() == UserRole.EMPLOYEE
                && !Objects.equals(employeeIdentityService.currentEmployee(actor).getId(), employeeId)) {
            throw new AccessDeniedException("Employees may only access their own records");
        }
        Employee employee = findByIdAndCompany(employeeId, company);
        tenantSecurityService.assertEmployeeAccess(actor, employee);
        if (actor.getRole() == UserRole.EMPLOYEE && !"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Employee profile is inactive");
        }
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void linkLoginAccount(User actor, Long employeeId, Long userId) {
        employeeIdentityService.linkAccount(actor, employeeId, userId);
    }

    private Employee findByIdAndCompany(Long employeeId, Company company) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee does not belong to this company");
        }
        return employee;
    }

    private Company currentCompany(User actor) {
        if (actor == null || actor.getCompany() == null) {
            throw new AccessDeniedException("Company context missing");
        }
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }

    private void requireManagementRole(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Employee management role required");
        }
    }

    private Department findActiveDepartment(Company company, Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ApiException("Department not found"));
        if (!Objects.equals(department.getCompanyId(), company.getId())
                || !Objects.equals(department.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Department does not belong to your company");
        }
        if (!department.isActive()) {
            throw new ApiException("Department is inactive");
        }
        return department;
    }

    private Designation findActiveDesignation(Company company, Long designationId) {
        Designation designation = designationRepository.findById(designationId)
                .orElseThrow(() -> new ApiException("Designation not found"));
        if (!Objects.equals(designation.getCompanyId(), company.getId())
                || !Objects.equals(designation.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Designation does not belong to your company");
        }
        if (!designation.isActive()) {
            throw new ApiException("Designation is inactive");
        }
        return designation;
    }

    private void validateUniqueFields(Long companyId, EmployeeRequest request, Long existingId) {
        boolean duplicateNumber = existingId == null
                ? employeeRepository.existsByCompanyIdAndEmployeeNumberIgnoreCase(companyId, request.getEmployeeNumber().trim())
                : employeeRepository.existsByCompanyIdAndEmployeeNumberIgnoreCaseAndIdNot(companyId, request.getEmployeeNumber().trim(), existingId);
        if (duplicateNumber) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee number already exists in this company");
        }

        boolean duplicateEmail = existingId == null
                ? employeeRepository.existsByCompanyIdAndEmailIgnoreCase(companyId, request.getEmail().trim())
                : employeeRepository.existsByCompanyIdAndEmailIgnoreCaseAndIdNot(companyId, request.getEmail().trim(), existingId);
        if (duplicateEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee email already exists in this company");
        }
    }

    private void validateJoiningDate(EmployeeRequest request) {
        if (request.getDateOfBirth() != null && request.getDateOfJoining().isBefore(request.getDateOfBirth())) {
            throw new ApiException("Date of joining cannot be before date of birth");
        }
    }

    private void applyRequest(Employee employee, EmployeeRequest request, Department department, Designation designation) {
        if (request.getReportingManagerId() != null) {
            Employee manager = employeeRepository.findById(request.getReportingManagerId())
                    .orElseThrow(() -> new ApiException("Reporting manager not found"));
            if (!Objects.equals(manager.getCompanyId(), employee.getCompanyId())
                    || !Objects.equals(manager.getTenantId(), employee.getTenantId())) {
                throw new AccessDeniedException("Reporting manager must belong to your company");
            }
            if (Objects.equals(manager.getId(), employee.getId())) {
                throw new ApiException("An employee cannot report to themselves");
            }
        }

        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setEmployeeNumber(request.getEmployeeNumber().trim());
        employee.setEmail(request.getEmail().trim());
        employee.setPhone(request.getPhone().trim());
        employee.setAddress(request.getAddress().trim());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setGender(emptyToNull(request.getGender()));
        employee.setEmploymentStatus(normalize(request.getEmploymentType()));
        employee.setJobTitle(request.getJobTitle().trim());
        employee.setReportingManagerId(request.getReportingManagerId());
        employee.setEmergencyContactName(emptyToNull(request.getEmergencyContactName()));
        employee.setEmergencyContactPhone(emptyToNull(request.getEmergencyContactPhone()));
        employee.setEmergencyContactRelationship(emptyToNull(request.getEmergencyContactRelationship()));
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setDateOfJoining(request.getDateOfJoining());
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

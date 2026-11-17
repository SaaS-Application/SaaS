package com.payroll.saas.service;

import com.payroll.saas.dto.*;
import com.payroll.saas.entity.*;
import com.payroll.saas.exception.ApiException;
import com.payroll.saas.repository.*;
import com.payroll.saas.security.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    private static final BigDecimal MIN_MONEY = MAX_MONEY.negate();
    private static final int MONEY_SCALE = 2;
    private static final int CALCULATION_SCALE = 8;

    private final SalaryComponentDefinitionRepository componentRepository;
    private final SalaryStructureRepository structureRepository;
    private final SalaryStructureComponentRepository structureComponentRepository;
    private final SalaryAssignmentRepository assignmentRepository;
    private final SalaryAssignmentComponentRepository assignmentComponentRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final TenantSecurityService tenantSecurityService;
    private final EmployeeIdentityService employeeIdentityService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SalaryComponentResponse> components(User actor) {
        Company company = managedCompany(actor);
        return componentRepository.findByCompanyIdAndTenantIdOrderByName(company.getId(), company.getTenantId())
                .stream().map(SalaryComponentResponse::from).toList();
    }

    @Transactional
    public SalaryComponentResponse createComponent(User actor, SalaryComponentRequest input) {
        Company company = managedCompany(actor);
        validateEffectiveDates(input.getEffectiveFrom(), input.getEffectiveTo(), "Component");
        String code = normalizeCode(input.getCode());
        if (componentRepository.existsByCompanyIdAndTenantIdAndCodeIgnoreCase(
                company.getId(), company.getTenantId(), code)) {
            throw conflict("Salary component code already exists in this company");
        }
        SalaryComponentDefinition component = new SalaryComponentDefinition();
        component.setCompanyId(company.getId());
        component.setTenantId(company.getTenantId());
        applyComponent(component, input, code);
        component.setActive(true);
        try {
            SalaryComponentResponse response = SalaryComponentResponse.from(componentRepository.saveAndFlush(component));
            audit(actor, company, "SALARY_COMPONENT_CREATED", "Created salary component " + code);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Salary component code already exists in this company");
        }
    }

    @Transactional
    public SalaryComponentResponse updateComponent(User actor, Long componentId, SalaryComponentRequest input) {
        Company company = managedCompany(actor);
        SalaryComponentDefinition component = findComponent(componentId, company);
        validateEffectiveDates(input.getEffectiveFrom(), input.getEffectiveTo(), "Component");
        String code = normalizeCode(input.getCode());
        if (componentRepository.existsByCompanyIdAndTenantIdAndCodeIgnoreCaseAndIdNot(
                company.getId(), company.getTenantId(), code, componentId)) {
            throw conflict("Salary component code already exists in this company");
        }
        if ((component.getType() != input.getType()
                || component.getCalculationMethod() != input.getCalculationMethod())
                && structureComponentRepository.existsByComponentIdAndCompanyIdAndTenantId(
                componentId, company.getId(), company.getTenantId())) {
            throw conflict("Component type and calculation method cannot change after the component is used in a structure");
        }
        applyComponent(component, input, code);
        SalaryComponentResponse response = SalaryComponentResponse.from(componentRepository.save(component));
        audit(actor, company, "SALARY_COMPONENT_UPDATED", "Updated salary component " + code);
        return response;
    }

    @Transactional
    public SalaryComponentResponse setComponentActive(User actor, Long componentId, boolean active) {
        Company company = managedCompany(actor);
        SalaryComponentDefinition component = findComponent(componentId, company);
        component.setActive(active);
        SalaryComponentResponse response = SalaryComponentResponse.from(componentRepository.save(component));
        audit(actor, company, active ? "SALARY_COMPONENT_ACTIVATED" : "SALARY_COMPONENT_DEACTIVATED",
                "Changed salary component " + component.getCode() + " active status to " + active);
        return response;
    }

    @Transactional(readOnly = true)
    public List<SalaryStructureResponse> structures(User actor) {
        Company company = managedCompany(actor);
        return structureRepository.findByCompanyIdAndTenantIdOrderByName(company.getId(), company.getTenantId())
                .stream().map(structure -> structureResponse(structure, company)).toList();
    }

    @Transactional(readOnly = true)
    public SalaryStructureResponse structure(User actor, Long structureId) {
        Company company = managedCompany(actor);
        return structureResponse(findStructure(structureId, company), company);
    }

    @Transactional
    public SalaryStructureResponse createStructure(User actor, SalaryStructureRequest input) {
        Company company = managedCompany(actor);
        validateEffectiveDates(input.getEffectiveFrom(), input.getEffectiveTo(), "Structure");
        String code = normalizeCode(input.getCode());
        if (structureRepository.existsByCompanyIdAndTenantIdAndCodeIgnoreCase(
                company.getId(), company.getTenantId(), code)) {
            throw conflict("Salary structure code already exists in this company");
        }

        SalaryStructure structure = new SalaryStructure();
        structure.setCompanyId(company.getId());
        structure.setTenantId(company.getTenantId());
        applyStructure(structure, input, code);
        structure.setActive(true);
        List<SalaryStructureComponent> lines = prepareStructureComponents(structure, company, input.getComponents());
        SalaryStructure saved = structureRepository.saveAndFlush(structure);
        persistStructureComponents(saved, company, lines);
        SalaryStructureResponse response = structureResponse(saved, company);
        audit(actor, company, "SALARY_STRUCTURE_CREATED", "Created salary structure " + code);
        return response;
    }

    @Transactional
    public SalaryStructureResponse updateStructure(User actor, Long structureId, SalaryStructureRequest input) {
        Company company = managedCompany(actor);
        SalaryStructure structure = structureRepository.findForUpdate(structureId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Salary structure not found"));
        validateEffectiveDates(input.getEffectiveFrom(), input.getEffectiveTo(), "Structure");
        String code = normalizeCode(input.getCode());
        if (structureRepository.existsByCompanyIdAndTenantIdAndCodeIgnoreCaseAndIdNot(
                company.getId(), company.getTenantId(), code, structureId)) {
            throw conflict("Salary structure code already exists in this company");
        }
        applyStructure(structure, input, code);
        List<SalaryStructureComponent> lines = prepareStructureComponents(structure, company, input.getComponents());
        structureRepository.save(structure);
        persistStructureComponents(structure, company, lines);
        SalaryStructureResponse response = structureResponse(structure, company);
        audit(actor, company, "SALARY_STRUCTURE_UPDATED", "Updated salary structure " + code);
        return response;
    }

    @Transactional
    public SalaryStructureResponse setStructureActive(User actor, Long structureId, boolean active) {
        Company company = managedCompany(actor);
        SalaryStructure structure = structureRepository.findForUpdate(
                        structureId, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Salary structure not found"));
        structure.setActive(active);
        SalaryStructureResponse response = structureResponse(structureRepository.save(structure), company);
        audit(actor, company, active ? "SALARY_STRUCTURE_ACTIVATED" : "SALARY_STRUCTURE_DEACTIVATED",
                "Changed salary structure " + structure.getCode() + " active status to " + active);
        return response;
    }

    @Transactional
    public SalaryAssignmentResponse assign(User actor, SalaryAssignmentRequest input) {
        Company company = managedCompany(actor);
        Employee employee = employeeRepository.findForUpdate(
                        input.getEmployeeId(), company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new ApiException("Salary cannot be assigned to an inactive employee");
        }
        validateEffectiveDates(input.getEffectiveFrom(), input.getEffectiveTo(), "Assignment");

        SalaryStructure structure = findStructure(input.getSalaryStructureId(), company);
        if (!structure.isActive()) throw new ApiException("Salary structure is inactive");
        validateAssignmentDates(structure, input.getEffectiveFrom(), input.getEffectiveTo());
        List<SalaryStructureComponent> structureLines = structureComponentRepository
                .findBySalaryStructureIdAndCompanyIdAndTenantIdOrderById(
                        structure.getId(), company.getId(), company.getTenantId());
        Map<Long, SalaryComponentDefinition> definitions = loadDefinitions(company, structureLines);
        for (SalaryComponentDefinition definition : definitions.values()) {
            if (!definition.isActive()) throw new ApiException("A salary structure component is inactive");
            validateDateCoverage(definition.getEffectiveFrom(), definition.getEffectiveTo(),
                    input.getEffectiveFrom(), input.getEffectiveTo(), "Salary component " + definition.getCode());
        }
        Calculation calculation = calculate(structureLines, definitions);

        List<SalaryAssignment> overlapping = assignmentRepository.findOverlapping(
                employee.getId(), company.getId(), company.getTenantId(),
                input.getEffectiveFrom(), input.getEffectiveTo());
        if (overlapping.size() > 1) {
            throw conflict("Existing salary assignment history overlaps; resolve it before assigning another structure");
        }
        if (!overlapping.isEmpty()) {
            SalaryAssignment current = overlapping.get(0);
            if (!current.getEffectiveFrom().isBefore(input.getEffectiveFrom()) || current.getEffectiveTo() != null) {
                throw conflict("Salary assignment dates overlap an existing assignment");
            }
            current.setEffectiveTo(input.getEffectiveFrom().minusDays(1));
            assignmentRepository.save(current);
            audit(actor, company, "SALARY_ASSIGNMENT_ENDED",
                    "Ended salary assignment " + current.getId() + " before a new assignment");
        }

        SalaryAssignment assignment = SalaryAssignment.builder()
                .companyId(company.getId()).tenantId(company.getTenantId()).employeeId(employee.getId())
                .salaryStructureId(structure.getId()).structureCodeSnapshot(structure.getCode())
                .structureNameSnapshot(structure.getName()).effectiveFrom(input.getEffectiveFrom())
                .effectiveTo(input.getEffectiveTo()).active(true).grossEarnings(calculation.grossEarnings())
                .totalDeductions(calculation.totalDeductions()).illustrativeNet(calculation.illustrativeNet())
                .assignedBy(actor.getId()).build();
        SalaryAssignment saved = assignmentRepository.saveAndFlush(assignment);
        for (SalaryStructureComponent line : structureLines) {
            SalaryComponentDefinition definition = definitions.get(line.getComponentId());
            SalaryStructureComponentResponse calculatedLine = calculation.lines().stream()
                    .filter(item -> item.getComponentId().equals(definition.getId())).findFirst()
                    .orElseThrow(() -> new ApiException("Salary component calculation was incomplete"));
            SalaryAssignmentComponent snapshot = SalaryAssignmentComponent.builder()
                    .companyId(company.getId()).tenantId(company.getTenantId()).salaryAssignmentId(saved.getId())
                    .componentId(definition.getId()).code(definition.getCode()).name(definition.getName())
                    .type(definition.getType()).calculationMethod(definition.getCalculationMethod())
                    .value(line.getValue()).percentageBaseCode(calculatedLine.getPercentageBaseCode())
                    .taxable(definition.isTaxable()).calculatedAmount(calculatedLine.getCalculatedAmount()).build();
            assignmentComponentRepository.save(snapshot);
        }
        audit(actor, company, "SALARY_ASSIGNED",
                "Assigned salary structure " + structure.getCode() + " to employee " + employee.getId());
        return assignmentResponse(saved, company);
    }

    @Transactional(readOnly = true)
    public List<SalaryAssignmentResponse> employeeAssignments(User actor, Long employeeId) {
        Company company = managedCompany(actor);
        Employee employee = findEmployee(employeeId, company);
        return assignmentRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByEffectiveFromDescIdDesc(
                        employee.getId(), company.getId(), company.getTenantId())
                .stream().map(assignment -> assignmentResponse(assignment, company)).toList();
    }

    @Transactional(readOnly = true)
    public SalaryAssignmentResponse currentEmployeeSalary(User actor) {
        Employee employee = employeeIdentityService.currentEmployee(actor);
        Company company = companyForEmployee(actor);
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Employee profile is inactive");
        }
        SalaryAssignment assignment = assignmentRepository.findCurrent(
                        employee.getId(), company.getId(), company.getTenantId(), LocalDate.now(clock))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No salary assignment is effective today"));
        return assignmentResponse(assignment, company);
    }

    @Transactional(readOnly = true)
    public List<SalaryAssignmentResponse> employeeSalaryHistory(User actor) {
        Employee employee = employeeIdentityService.currentEmployee(actor);
        Company company = companyForEmployee(actor);
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Employee profile is inactive");
        }
        return assignmentRepository.findByEmployeeIdAndCompanyIdAndTenantIdOrderByEffectiveFromDescIdDesc(
                        employee.getId(), company.getId(), company.getTenantId())
                .stream().map(assignment -> assignmentResponse(assignment, company)).toList();
    }

    @Transactional(readOnly = true)
    public SalaryPlatformSummaryResponse superAdminSummary(User actor) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Super Admin role required");
        }
        LocalDate asOfDate = LocalDate.now(clock);
        List<SalaryCompanySummaryResponse> summaries = companyRepository.findAll().stream()
                .map(company -> SalaryCompanySummaryResponse.builder().companyId(company.getId())
                        .companyName(company.getName()).companyCode(company.getCode())
                        .activeStructures(structureRepository.countEffectiveByCompanyAndTenant(
                                company.getId(), company.getTenantId(), asOfDate))
                        .activeAssignments(assignmentRepository.countEffectiveByCompanyAndTenant(
                                company.getId(), company.getTenantId(), asOfDate)).build())
                .toList();
        return SalaryPlatformSummaryResponse.builder().companies(summaries.size())
                .activeStructures(summaries.stream().mapToLong(SalaryCompanySummaryResponse::getActiveStructures).sum())
                .activeAssignments(summaries.stream().mapToLong(SalaryCompanySummaryResponse::getActiveAssignments).sum())
                .companySummaries(summaries).build();
    }

    private List<SalaryStructureComponent> prepareStructureComponents(
            SalaryStructure structure, Company company, List<SalaryStructureComponentRequest> inputs) {
        Map<Long, SalaryComponentDefinition> definitions = new LinkedHashMap<>();
        Set<Long> ids = new HashSet<>();
        for (SalaryStructureComponentRequest input : inputs) {
            if (!ids.add(input.getComponentId())) throw new ApiException("A component may appear only once in a structure");
            SalaryComponentDefinition definition = findComponent(input.getComponentId(), company);
            if (!definition.isActive()) throw new ApiException("Inactive salary components cannot be added to a structure");
            validateDateCoverage(definition.getEffectiveFrom(), definition.getEffectiveTo(),
                    structure.getEffectiveFrom(), structure.getEffectiveTo(), "Salary component " + definition.getCode());
            definitions.put(definition.getId(), definition);
            input.setValue(normalizeValue(input.getValue(), definition.getCalculationMethod()));
            if (definition.getCalculationMethod() == SalaryCalculationMethod.FIXED_AMOUNT
                    && input.getPercentageBaseComponentId() != null) {
                throw new ApiException("Fixed amount components cannot have a percentage base");
            }
            if (definition.getCalculationMethod() == SalaryCalculationMethod.PERCENTAGE
                    && input.getPercentageBaseComponentId() == null) {
                throw new ApiException("Percentage components require an earning component as their base");
            }
        }
        for (SalaryStructureComponentRequest input : inputs) {
            SalaryComponentDefinition definition = definitions.get(input.getComponentId());
            Long baseId = input.getPercentageBaseComponentId();
            if (baseId != null) {
                SalaryComponentDefinition base = definitions.get(baseId);
                if (base == null || base.getType() != SalaryComponentType.EARNING) {
                    throw new ApiException("Percentage base must be an earning component in this structure");
                }
                if (baseId.equals(definition.getId())) {
                    throw new ApiException("A salary component cannot use itself as a percentage base");
                }
            }
        }

        List<SalaryStructureComponent> lines = inputs.stream()
                .map(input -> SalaryStructureComponent.builder().companyId(company.getId())
                        .tenantId(company.getTenantId())
                        .componentId(input.getComponentId())
                        .percentageBaseComponentId(input.getPercentageBaseComponentId())
                        .value(input.getValue()).build())
                .toList();
        calculate(lines, definitions);
        return lines;
    }

    private void persistStructureComponents(
            SalaryStructure structure, Company company, List<SalaryStructureComponent> lines) {
        lines.forEach(line -> line.setSalaryStructureId(structure.getId()));
        structureComponentRepository.deleteBySalaryStructureIdAndCompanyIdAndTenantId(
                structure.getId(), company.getId(), company.getTenantId());
        structureComponentRepository.saveAll(lines);
    }

    private SalaryStructureResponse structureResponse(SalaryStructure structure, Company company) {
        List<SalaryStructureComponent> lines = structureComponentRepository
                .findBySalaryStructureIdAndCompanyIdAndTenantIdOrderById(
                        structure.getId(), company.getId(), company.getTenantId());
        Map<Long, SalaryComponentDefinition> definitions = loadDefinitions(company, lines);
        Calculation calculation = calculate(lines, definitions);
        return SalaryStructureResponse.builder().id(structure.getId()).code(structure.getCode())
                .name(structure.getName()).effectiveFrom(structure.getEffectiveFrom())
                .effectiveTo(structure.getEffectiveTo()).active(structure.isActive())
                .components(calculation.lines()).grossEarnings(calculation.grossEarnings())
                .totalDeductions(calculation.totalDeductions()).illustrativeNet(calculation.illustrativeNet()).build();
    }

    private Calculation calculate(
            List<SalaryStructureComponent> lines, Map<Long, SalaryComponentDefinition> definitions) {
        if (lines.isEmpty()) throw new ApiException("A salary structure must have at least one component");
        Map<Long, SalaryStructureComponent> byComponent = lines.stream()
                .collect(Collectors.toMap(SalaryStructureComponent::getComponentId, line -> line));
        Map<Long, BigDecimal> amounts = new HashMap<>();
        Set<Long> visiting = new HashSet<>();
        for (SalaryStructureComponent line : lines) {
            calculateComponent(line.getComponentId(), byComponent, definitions, amounts, visiting);
        }

        List<SalaryStructureComponentResponse> responses = new ArrayList<>();
        BigDecimal gross = ZERO_MONEY;
        BigDecimal deductions = ZERO_MONEY;
        for (SalaryStructureComponent line : lines) {
            SalaryComponentDefinition definition = definitions.get(line.getComponentId());
            BigDecimal amount = amounts.get(definition.getId());
            if (definition.getType() == SalaryComponentType.EARNING) gross = gross.add(amount);
            else deductions = deductions.add(amount);
            String baseCode = line.getPercentageBaseComponentId() == null ? null
                    : definitions.get(line.getPercentageBaseComponentId()).getCode();
            responses.add(SalaryStructureComponentResponse.builder().componentId(definition.getId())
                    .code(definition.getCode()).name(definition.getName()).type(definition.getType())
                    .calculationMethod(definition.getCalculationMethod()).value(line.getValue())
                    .percentageBaseCode(baseCode).taxable(definition.isTaxable())
                    .calculatedAmount(amount).build());
        }
        gross = gross.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        deductions = deductions.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        validateMonetaryAmount(gross, "Gross earnings");
        validateMonetaryAmount(deductions, "Total deductions");
        BigDecimal net = gross.subtract(deductions).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        validateMonetaryAmount(net, "Net salary");
        return new Calculation(responses, gross, deductions, net);
    }

    private BigDecimal calculateComponent(
            Long componentId, Map<Long, SalaryStructureComponent> lines,
            Map<Long, SalaryComponentDefinition> definitions,
            Map<Long, BigDecimal> amounts, Set<Long> visiting) {
        BigDecimal cached = amounts.get(componentId);
        if (cached != null) return cached;
        SalaryStructureComponent line = lines.get(componentId);
        SalaryComponentDefinition definition = definitions.get(componentId);
        if (line == null || definition == null) throw new ApiException("Salary structure references an invalid component");
        if (!visiting.add(componentId)) throw new ApiException("Salary component percentage bases contain a cycle");

        BigDecimal calculated;
        if (definition.getCalculationMethod() == SalaryCalculationMethod.FIXED_AMOUNT) {
            calculated = line.getValue().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } else {
            Long baseId = line.getPercentageBaseComponentId();
            SalaryComponentDefinition baseDefinition = definitions.get(baseId);
            if (baseDefinition == null || baseDefinition.getType() != SalaryComponentType.EARNING) {
                throw new ApiException("Percentage base must be an earning component in this structure");
            }
            BigDecimal baseAmount = calculateComponent(baseId, lines, definitions, amounts, visiting);
            calculated = baseAmount.multiply(line.getValue())
                    .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        calculated = validateMonetaryAmount(calculated, "Calculated salary component amount");
        visiting.remove(componentId);
        amounts.put(componentId, calculated);
        return calculated;
    }

    private Map<Long, SalaryComponentDefinition> loadDefinitions(
            Company company, List<SalaryStructureComponent> lines) {
        Map<Long, SalaryComponentDefinition> definitions = new LinkedHashMap<>();
        for (SalaryStructureComponent line : lines) {
            SalaryComponentDefinition definition = findComponent(line.getComponentId(), company);
            definitions.put(definition.getId(), definition);
        }
        return definitions;
    }

    private SalaryAssignmentResponse assignmentResponse(SalaryAssignment assignment, Company company) {
        List<SalaryAssignmentComponentResponse> components = assignmentComponentRepository
                .findBySalaryAssignmentIdAndCompanyIdAndTenantIdOrderById(
                        assignment.getId(), company.getId(), company.getTenantId())
                .stream().map(component -> SalaryAssignmentComponentResponse.builder()
                        .code(component.getCode()).name(component.getName()).type(component.getType())
                        .calculationMethod(component.getCalculationMethod()).value(component.getValue())
                        .percentageBaseCode(component.getPercentageBaseCode()).taxable(component.isTaxable())
                        .calculatedAmount(component.getCalculatedAmount()).build())
                .toList();
        return SalaryAssignmentResponse.builder().id(assignment.getId())
                .structureCode(assignment.getStructureCodeSnapshot())
                .structureName(assignment.getStructureNameSnapshot())
                .effectiveFrom(assignment.getEffectiveFrom()).effectiveTo(assignment.getEffectiveTo())
                .active(assignment.isActive()).grossEarnings(assignment.getGrossEarnings())
                .totalDeductions(assignment.getTotalDeductions())
                .illustrativeNet(assignment.getIllustrativeNet()).components(components).build();
    }

    private void applyComponent(SalaryComponentDefinition component, SalaryComponentRequest input, String code) {
        component.setCode(code);
        component.setName(input.getName().trim());
        component.setType(input.getType());
        component.setCalculationMethod(input.getCalculationMethod());
        component.setTaxable(input.isTaxable());
        component.setEffectiveFrom(input.getEffectiveFrom());
        component.setEffectiveTo(input.getEffectiveTo());
    }

    private void applyStructure(SalaryStructure structure, SalaryStructureRequest input, String code) {
        structure.setCode(code);
        structure.setName(input.getName().trim());
        structure.setEffectiveFrom(input.getEffectiveFrom());
        structure.setEffectiveTo(input.getEffectiveTo());
    }

    private void validateAssignmentDates(SalaryStructure structure, LocalDate from, LocalDate to) {
        if (from.isBefore(structure.getEffectiveFrom())
                || (structure.getEffectiveTo() != null
                && (to == null || to.isAfter(structure.getEffectiveTo())))) {
            throw new ApiException("Assignment dates must fall within the salary structure's effective period");
        }
    }

    private void validateEffectiveDates(LocalDate from, LocalDate to, String label) {
        if (from == null || (to != null && to.isBefore(from))) {
            throw new ApiException(label + " effective date range is invalid");
        }
    }

    private void validateDateCoverage(
            LocalDate componentFrom, LocalDate componentTo, LocalDate assignmentFrom,
            LocalDate assignmentTo, String label) {
        if (assignmentFrom.isBefore(componentFrom)
                || (componentTo != null && (assignmentTo == null || assignmentTo.isAfter(componentTo)))) {
            throw new ApiException(label + " is not effective for the full assignment period");
        }
    }

    private BigDecimal normalizeValue(BigDecimal value, SalaryCalculationMethod method) {
        if (value == null || value.signum() < 0) throw new ApiException("Salary component value cannot be negative");
        try {
            return value.setScale(method == SalaryCalculationMethod.FIXED_AMOUNT ? MONEY_SCALE : 4,
                    RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(method == SalaryCalculationMethod.FIXED_AMOUNT
                    ? "Fixed salary amounts may have at most two decimal places"
                    : "Percentage rates may have at most four decimal places");
        }
    }

    static BigDecimal validateMonetaryAmount(BigDecimal amount, String label) {
        BigDecimal currencyAmount;
        try {
            currencyAmount = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(label + " must have no more than two decimal places");
        }
        if (currencyAmount.compareTo(MIN_MONEY) < 0 || currencyAmount.compareTo(MAX_MONEY) > 0) {
            throw new ApiException(label + " exceeds the supported range of "
                    + MIN_MONEY.toPlainString() + " to " + MAX_MONEY.toPlainString());
        }
        return currencyAmount;
    }

    private SalaryComponentDefinition findComponent(Long id, Company company) {
        return componentRepository.findByIdAndCompanyIdAndTenantId(id, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Salary component not found"));
    }

    private SalaryStructure findStructure(Long id, Company company) {
        return structureRepository.findByIdAndCompanyIdAndTenantId(id, company.getId(), company.getTenantId())
                .orElseThrow(() -> new ApiException("Salary structure not found"));
    }

    private Employee findEmployee(Long employeeId, Company company) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException("Employee not found"));
        if (!Objects.equals(employee.getCompanyId(), company.getId())
                || !Objects.equals(employee.getTenantId(), company.getTenantId())) {
            throw new AccessDeniedException("Employee does not belong to your company");
        }
        return employee;
    }

    private Company companyForEmployee(User actor) {
        if (actor == null || actor.getCompany() == null) throw new AccessDeniedException("Company context missing");
        return tenantSecurityService.assertCompanyAccess(actor, actor.getCompany().getId());
    }

    private Company managedCompany(User actor) {
        if (actor == null || (actor.getRole() != UserRole.COMPANY_ADMIN && actor.getRole() != UserRole.HR_ADMIN)) {
            throw new AccessDeniedException("Company or HR administrator role required");
        }
        return companyForEmployee(actor);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private void audit(User actor, Company company, String action, String details) {
        auditLogService.record(action, actor.getUsername(), actor.getRole().name(),
                company.getTenantId(), company.getId(), details);
    }

    private record Calculation(
            List<SalaryStructureComponentResponse> lines,
            BigDecimal grossEarnings,
            BigDecimal totalDeductions,
            BigDecimal illustrativeNet) {
    }
}

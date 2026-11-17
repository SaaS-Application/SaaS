package com.payroll.saas.dto;

import com.payroll.saas.entity.Employee;

public record EmployeeSummaryResponse(
        Long id,
        String employeeNumber,
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        Long departmentId,
        String departmentName,
        Long designationId,
        String designationName,
        String employmentType,
        String status) {

    public static EmployeeSummaryResponse from(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getJobTitle(),
                employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee.getDesignation() == null ? null : employee.getDesignation().getId(),
                employee.getDesignation() == null ? null : employee.getDesignation().getName(),
                employee.getEmploymentStatus(),
                employee.getStatus());
    }
}

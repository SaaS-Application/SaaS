package com.payroll.saas.dto;

import com.payroll.saas.entity.Employee;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class EmployeeResponse {
    Long id;
    String employeeNumber;
    String firstName;
    String lastName;
    String email;
    String phone;
    String address;
    LocalDate dateOfBirth;
    String gender;
    LocalDate dateOfJoining;
    String employmentType;
    String jobTitle;
    Long reportingManagerId;
    String emergencyContactName;
    String emergencyContactPhone;
    String emergencyContactRelationship;
    Long departmentId;
    String departmentName;
    Long designationId;
    String designationName;
    String status;

    public static EmployeeResponse from(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .address(employee.getAddress())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender())
                .dateOfJoining(employee.getDateOfJoining())
                .employmentType(employee.getEmploymentStatus())
                .jobTitle(employee.getJobTitle())
                .reportingManagerId(employee.getReportingManagerId())
                .emergencyContactName(employee.getEmergencyContactName())
                .emergencyContactPhone(employee.getEmergencyContactPhone())
                .emergencyContactRelationship(employee.getEmergencyContactRelationship())
                .departmentId(employee.getDepartment() == null ? null : employee.getDepartment().getId())
                .departmentName(employee.getDepartment() == null ? null : employee.getDepartment().getName())
                .designationId(employee.getDesignation() == null ? null : employee.getDesignation().getId())
                .designationName(employee.getDesignation() == null ? null : employee.getDesignation().getName())
                .status(employee.getStatus())
                .build();
    }
}

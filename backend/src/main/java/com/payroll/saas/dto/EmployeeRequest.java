package com.payroll.saas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeRequest {
    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Size(max = 40)
    private String employeeNumber;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 40)
    private String phone;

    @NotBlank
    @Size(max = 500)
    private String address;

    @Past
    private LocalDate dateOfBirth;

    @Pattern(regexp = "MALE|FEMALE|NON_BINARY|OTHER|PREFER_NOT_TO_SAY", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String gender;

    @NotBlank
    @Pattern(regexp = "FULL_TIME|PART_TIME|CONTRACT|INTERN|TEMPORARY", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String employmentType;

    @NotBlank
    @Size(max = 120)
    private String jobTitle;

    private Long reportingManagerId;

    @Size(max = 120)
    private String emergencyContactName;

    @Size(max = 40)
    private String emergencyContactPhone;

    @Size(max = 80)
    private String emergencyContactRelationship;

    @NotNull
    private Long departmentId;

    @NotNull
    private Long designationId;

    @NotNull
    @PastOrPresent
    private LocalDate dateOfJoining;

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String status;
}

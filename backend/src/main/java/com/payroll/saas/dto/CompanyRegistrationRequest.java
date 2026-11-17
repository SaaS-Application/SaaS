package com.payroll.saas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyRegistrationRequest {
    @NotBlank
    private String companyName;

    @NotBlank
    private String companyCode;

    @NotBlank
    @Email
    private String companyEmail;

    @NotBlank
    private String companyPhone;

    @NotBlank
    private String companyAddress;

    @NotBlank
    private String subscriptionPlan;

    @NotBlank
    private String adminUsername;

    @NotBlank
    private String adminPassword;
}

package com.payroll.saas.dto;

import com.payroll.saas.entity.Company;

public record CompanySummaryResponse(
        Long id,
        String name,
        String code,
        String email,
        String status,
        String subscriptionPlan) {

    public static CompanySummaryResponse from(Company company) {
        return new CompanySummaryResponse(
                company.getId(),
                company.getName(),
                company.getCode(),
                company.getEmail(),
                company.getStatus(),
                company.getSubscriptionPlan());
    }
}

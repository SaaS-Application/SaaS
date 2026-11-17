package com.payroll.saas.service;

import com.payroll.saas.entity.Company;
import com.payroll.saas.repository.CompanyRepository;
import com.payroll.saas.repository.EmployeeRepository;
import com.payroll.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getPlatformDashboard() {
        List<Company> companies = companyRepository.findAll();
        long totalCompanies = companies.size();
        long activeCompanies = companies.stream().filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus())).count();
        long inactiveCompanies = totalCompanies - activeCompanies;

        List<Map<String, Object>> recentCompanies = companies.stream()
                .limit(5)
                .map(company -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", company.getId());
                    item.put("name", company.getName());
                    item.put("code", company.getCode());
                    item.put("status", company.getStatus());
                    item.put("tenantId", company.getTenantId());
                    return item;
                })
                .toList();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalCompanies", totalCompanies);
        dashboard.put("totalEmployees", employeeRepository.count());
        dashboard.put("activeCompanies", activeCompanies);
        dashboard.put("inactiveCompanies", inactiveCompanies);
        dashboard.put("activeSubscriptions", activeCompanies);
        dashboard.put("platformUsers", userRepository.count());
        dashboard.put("recentCompanies", recentCompanies);
        dashboard.put("subscriptionRevenue", totalCompanies * 2500L);
        return dashboard;
    }
}

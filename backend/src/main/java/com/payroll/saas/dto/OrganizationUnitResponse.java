package com.payroll.saas.dto;

import com.payroll.saas.entity.Department;
import com.payroll.saas.entity.Designation;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrganizationUnitResponse {
    Long id;
    String name;
    boolean active;

    public static OrganizationUnitResponse from(Department department) {
        return OrganizationUnitResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .active(department.isActive())
                .build();
    }

    public static OrganizationUnitResponse from(Designation designation) {
        return OrganizationUnitResponse.builder()
                .id(designation.getId())
                .name(designation.getName())
                .active(designation.isActive())
                .build();
    }
}

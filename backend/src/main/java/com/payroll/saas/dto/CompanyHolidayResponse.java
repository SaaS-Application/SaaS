package com.payroll.saas.dto;

import com.payroll.saas.entity.CompanyHoliday;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class CompanyHolidayResponse {
    Long id;
    String name;
    LocalDate date;
    String description;
    boolean active;

    public static CompanyHolidayResponse from(CompanyHoliday holiday) {
        return CompanyHolidayResponse.builder().id(holiday.getId()).name(holiday.getName())
                .date(holiday.getDate()).description(holiday.getDescription()).active(holiday.isActive()).build();
    }
}

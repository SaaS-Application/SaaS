package com.payroll.saas.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LeaveOverviewResponse {
    Long companyId;
    long pendingApplications;
    long approvedApplications;
    long rejectedApplications;
    long cancellationRequests;
    long activeLeaveTypes;
    long activeCompanyHolidays;
}

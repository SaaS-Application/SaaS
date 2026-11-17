package com.payroll.saas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeaveTypeStatusRequest {
    @NotNull
    private Boolean active;
}

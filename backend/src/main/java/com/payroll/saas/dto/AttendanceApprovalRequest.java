package com.payroll.saas.dto;

import com.payroll.saas.entity.AttendanceApprovalDecision;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttendanceApprovalRequest {
    @NotNull
    private AttendanceApprovalDecision decision;
}

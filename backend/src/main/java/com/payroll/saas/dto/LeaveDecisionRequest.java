package com.payroll.saas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeaveDecisionRequest {
    @NotBlank
    private String decision;
    @Size(max = 1000)
    private String comment;
}

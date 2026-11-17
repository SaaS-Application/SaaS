package com.payroll.saas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeaveCancellationDecisionRequest {
    @NotNull
    private Boolean approve;
    @Size(max = 1000)
    private String comment;
}

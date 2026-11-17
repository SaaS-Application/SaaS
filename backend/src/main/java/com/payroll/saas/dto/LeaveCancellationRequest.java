package com.payroll.saas.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeaveCancellationRequest {
    @Size(max = 1000)
    private String comment;
}

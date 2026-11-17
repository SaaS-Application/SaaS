package com.payroll.saas.controller;

import com.payroll.saas.dto.PayslipSummaryResponse;
import com.payroll.saas.entity.User;
import com.payroll.saas.service.PayslipFile;
import com.payroll.saas.service.PayslipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payslips", description = "Read-only access to payslips generated from saved payroll snapshots.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Role, tenant, company, or suspension restriction"),
        @ApiResponse(responseCode = "404", description = "Payslip not found in the caller's permitted scope"),
        @ApiResponse(responseCode = "409", description = "Saved payslip calculation snapshot is missing or inconsistent")
})
public class PayslipController {

    private final PayslipService payslipService;

    @GetMapping("/employee/payslips")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "List the authenticated employee's processed payslips")
    public ResponseEntity<List<PayslipSummaryResponse>> employeePayslips(
            @RequestParam(required = false) String payPeriod,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payslipService.getEmployeePayslips(actor, payPeriod));
    }

    @GetMapping(value = "/employee/payslips/{payrollId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Download a processed payslip belonging to the authenticated employee")
    @ApiResponse(responseCode = "200", description = "PDF payslip",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> downloadEmployeePayslip(
            @PathVariable Long payrollId, @AuthenticationPrincipal User actor) {
        return pdfResponse(payslipService.downloadEmployeePayslip(actor, payrollId));
    }

    @GetMapping("/company-admin/payslips")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
    @Operation(summary = "List processed payslips for the authenticated company")
    public ResponseEntity<List<PayslipSummaryResponse>> companyPayslips(
            @RequestParam(required = false) String payPeriod,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payslipService.getCompanyPayslips(actor, payPeriod, search));
    }

    @GetMapping(value = "/company-admin/payslips/{payrollId}/download",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
    @Operation(summary = "Download a processed payslip from the authenticated company")
    @ApiResponse(responseCode = "200", description = "PDF payslip",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> downloadCompanyPayslip(
            @PathVariable Long payrollId, @AuthenticationPrincipal User actor) {
        return pdfResponse(payslipService.downloadCompanyPayslip(actor, payrollId));
    }

    @GetMapping("/super-admin/companies/{companyId}/payslips")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List processed payslips for one company as platform oversight")
    public ResponseEntity<List<PayslipSummaryResponse>> superAdminCompanyPayslips(
            @PathVariable Long companyId,
            @RequestParam(required = false) String payPeriod,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(payslipService.getSuperAdminCompanyPayslips(
                actor, companyId, payPeriod, search));
    }

    @GetMapping(value = "/super-admin/companies/{companyId}/payslips/{payrollId}/download",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Download a company's processed payslip for read-only platform oversight")
    @ApiResponse(responseCode = "200", description = "PDF payslip",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> downloadSuperAdminPayslip(
            @PathVariable Long companyId,
            @PathVariable Long payrollId,
            @AuthenticationPrincipal User actor) {
        return pdfResponse(payslipService.downloadSuperAdminPayslip(actor, companyId, payrollId));
    }

    private ResponseEntity<byte[]> pdfResponse(PayslipFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}

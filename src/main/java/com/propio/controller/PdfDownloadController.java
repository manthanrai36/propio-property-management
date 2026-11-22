package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.Document;
import com.propio.model.Lease;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.model.Tenant;
import com.propio.repository.DocumentRepository;
import com.propio.repository.LeaseRepository;
import com.propio.repository.PaymentRepository;
import com.propio.repository.TenantRepository;
import com.propio.service.LeaseService;
import com.propio.service.PdfGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller handling high-fidelity PDF document streaming with strict role & tenant-level
 * access controls:
 * 1. Payment Receipts: Only for settled (PAID) payments; tenants can only download their own.
 * 2. Lease Agreements: Official tenancy contracts; tenants can only download their own.
 * 3. Annual Statements: Yearly resident ledger; tenants can only download their own.
 * 4. Document Archive: Direct streaming of archived PDF records.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PdfDownloadController {

    private final PdfGenerationService pdfGenerationService;
    private final PaymentRepository paymentRepository;
    private final LeaseRepository leaseRepository;
    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final LeaseService leaseService;

    /**
     * Download or view Official Payment Receipt PDF.
     */
    @GetMapping("/payments/{id}/receipt/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public ResponseEntity<byte[]> downloadPaymentReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Payment payment = paymentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment invoice not found with ID: " + id));

        // Security: Tenant can only download their own receipts
        verifyTenantAccess(payment.getLease() != null && payment.getLease().getTenant() != null
                ? payment.getLease().getTenant().getId() : null, userDetails, "receipt");

        // Business Rule: Receipts are strictly for settled (PAID) payments
        if (payment.getStatus() != PaymentStatus.PAID) {
            log.warn("Receipt requested for unpaid payment ID {} (Status: {})", id, payment.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Official receipts are only generated for settled (PAID) payments. Current status: " + payment.getStatus()).getBytes());
        }

        byte[] pdfBytes = pdfGenerationService.generatePaymentReceiptPdf(payment);
        String filename = "Receipt-" + sanitize(payment.getTransactionReference()) + ".pdf";

        return buildPdfResponse(pdfBytes, filename);
    }

    /**
     * Download or view Residential Lease Agreement PDF.
     */
    @GetMapping("/leases/{id}/agreement/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public ResponseEntity<byte[]> downloadLeaseAgreement(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Lease lease = leaseRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lease agreement not found with ID: " + id));

        // Security: Tenant can only download their own lease agreements
        verifyTenantAccess(lease.getTenant() != null ? lease.getTenant().getId() : null,
                userDetails, "lease agreement");

        // Ensure Document record is archived in the database
        leaseService.ensureLeaseAgreementDocument(lease);

        byte[] pdfBytes = pdfGenerationService.generateLeaseAgreementPdf(lease);
        String unitNum = (lease.getUnit() != null && lease.getUnit().getUnitNumber() != null)
                ? sanitize(lease.getUnit().getUnitNumber()) : "Unit";
        String filename = "Lease-Agreement-Unit-" + unitNum + ".pdf";

        return buildPdfResponse(pdfBytes, filename);
    }

    /**
     * Download or view Annual Resident Financial Statement PDF.
     */
    @GetMapping("/tenants/{id}/statement/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public ResponseEntity<byte[]> downloadYearlyStatement(
            @PathVariable Long id,
            @RequestParam(value = "year", required = false) Integer year,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant profile not found with ID: " + id));

        // Security: Tenant can only download their own annual statement
        verifyTenantAccess(tenant.getId(), userDetails, "annual statement");

        int targetYear = (year != null && year >= 2000 && year <= 2100)
                ? year : LocalDate.now().getYear();

        LocalDate yearStart = LocalDate.of(targetYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(targetYear, 12, 31);

        List<Payment> yearPayments = paymentRepository.findByTenantIdAndDueDateBetweenWithDetails(
                tenant.getId(), yearStart, yearEnd);

        byte[] pdfBytes = pdfGenerationService.generateYearlyStatementPdf(tenant, targetYear, yearPayments);
        String filename = "Annual-Statement-" + sanitize(tenant.getFullName()) + "-" + targetYear + ".pdf";

        return buildPdfResponse(pdfBytes, filename);
    }

    /**
     * Download archived document by ID.
     */
    @GetMapping("/documents/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Document doc = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));

        // Security: Tenant can only download their own documents
        verifyTenantAccess(doc.getTenant() != null ? doc.getTenant().getId() : null,
                userDetails, "document");

        byte[] pdfBytes;
        if (doc.getLease() != null) {
            pdfBytes = pdfGenerationService.generateLeaseAgreementPdf(doc.getLease());
        } else {
            // Fallback for document records linked to tenant
            int curYear = doc.getCreatedAt() != null ? doc.getCreatedAt().getYear() : LocalDate.now().getYear();
            LocalDate start = LocalDate.of(curYear, 1, 1);
            LocalDate end = LocalDate.of(curYear, 12, 31);
            List<Payment> payments = paymentRepository.findByTenantIdAndDueDateBetweenWithDetails(
                    doc.getTenant().getId(), start, end);
            pdfBytes = pdfGenerationService.generateYearlyStatementPdf(doc.getTenant(), curYear, payments);
        }

        String filename = doc.getFileName() != null ? doc.getFileName() : "Document-" + id + ".pdf";
        return buildPdfResponse(pdfBytes, filename);
    }

    // =========================================================================
    // Helpers & Security Invariants
    // =========================================================================

    private void verifyTenantAccess(Long recordTenantId, CustomUserDetails userDetails, String resourceType) {
        if (userDetails != null && userDetails.isTenant()) {
            Long loggedTenantId = userDetails.getTenantId();
            if (loggedTenantId == null || recordTenantId == null || !loggedTenantId.equals(recordTenantId)) {
                log.warn("Access Denied: Tenant user #{} attempted to download unauthorized {} for Tenant #{}",
                        loggedTenantId, resourceType, recordTenantId);
                throw new AccessDeniedException("Forbidden: You are only authorized to access and download " +
                        resourceType + " documents for your own resident account.");
            }
        }
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private String sanitize(String input) {
        if (input == null) return "document";
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}

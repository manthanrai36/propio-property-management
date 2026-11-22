package com.propio.service;

import com.lowagie.text.pdf.PdfReader;
import com.propio.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdfGenerationService Unit Tests")
class PdfGenerationServiceTest {

    private PdfGenerationService pdfGenerationService;
    private Tenant testTenant;
    private Property testProperty;
    private Unit testUnit;
    private Lease testLease;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        pdfGenerationService = new PdfGenerationService();

        testTenant = Tenant.builder()
                .id(1L)
                .firstName("Leslie")
                .lastName("Alexander")
                .email("tenant@propio.com")
                .phone("(555) 234-5678")
                .emergencyContact("Marcus Alexander (555) 999-0000")
                .occupation("Senior UI/UX Designer")
                .build();

        testProperty = Property.builder()
                .id(10L)
                .name("Oak Ridge Apartments")
                .address("1042 Evergreen Terrace")
                .city("Springfield")
                .state("IL")
                .zipCode("62704")
                .propertyType("Multi-family Apartment")
                .build();

        testUnit = Unit.builder()
                .id(101L)
                .unitNumber("Apt 4B")
                .bedrooms(2)
                .bathrooms(1)
                .squareFeet(950.0)
                .property(testProperty)
                .status(UnitStatus.OCCUPIED)
                .build();

        testLease = Lease.builder()
                .id(50L)
                .tenant(testTenant)
                .unit(testUnit)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .monthlyRent(new BigDecimal("1850.00"))
                .securityDeposit(new BigDecimal("1850.00"))
                .active(true)
                .build();

        testPayment = Payment.builder()
                .id(501L)
                .lease(testLease)
                .amount(new BigDecimal("1850.00"))
                .lateFee(BigDecimal.ZERO)
                .dueDate(LocalDate.of(2026, 11, 1))
                .paidDate(LocalDate.of(2026, 11, 2))
                .paymentMethod("BANK_TRANSFER")
                .status(PaymentStatus.PAID)
                .transactionReference("TXN-20261101-88A9F")
                .notes("Monthly rent for November 2026")
                .build();
    }

    @Test
    @DisplayName("Should successfully generate a valid PDF receipt with %PDF- header and page count >= 1")
    void testGeneratePaymentReceiptPdf_Success() throws IOException {
        byte[] pdfBytes = pdfGenerationService.generatePaymentReceiptPdf(testPayment);

        assertNotNull(pdfBytes, "PDF byte array should not be null");
        assertTrue(pdfBytes.length > 1000, "PDF size should be substantial (actual: " + pdfBytes.length + " bytes)");

        // Verify PDF Magic Bytes (%PDF-)
        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "Generated file should have valid PDF magic byte header");

        // Parse with OpenPDF PdfReader to ensure structural integrity
        PdfReader reader = new PdfReader(pdfBytes);
        assertTrue(reader.getNumberOfPages() >= 1, "Receipt PDF should have at least 1 page");
        reader.close();
    }

    @Test
    @DisplayName("Should generate valid receipt when late fees are assessed")
    void testGeneratePaymentReceiptPdf_WithLateFee() throws IOException {
        testPayment.setLateFee(new BigDecimal("92.50"));
        testPayment.setAmount(new BigDecimal("1942.50"));

        byte[] pdfBytes = pdfGenerationService.generatePaymentReceiptPdf(testPayment);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000);

        PdfReader reader = new PdfReader(pdfBytes);
        assertEquals(1, reader.getNumberOfPages());
        reader.close();
    }

    @Test
    @DisplayName("Should successfully generate a complete Residential Lease Agreement PDF")
    void testGenerateLeaseAgreementPdf_Success() throws IOException {
        byte[] pdfBytes = pdfGenerationService.generateLeaseAgreementPdf(testLease);

        assertNotNull(pdfBytes, "Lease agreement PDF byte array should not be null");
        assertTrue(pdfBytes.length > 2000, "Lease agreement should contain full sections");

        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "Should start with valid %PDF- header");

        PdfReader reader = new PdfReader(pdfBytes);
        assertTrue(reader.getNumberOfPages() >= 1, "Lease agreement should have at least 1 page");
        reader.close();
    }

    @Test
    @DisplayName("Should successfully generate an Annual Resident Financial Statement PDF")
    void testGenerateYearlyStatementPdf_Success() throws IOException {
        List<Payment> yearPayments = new ArrayList<>();
        yearPayments.add(testPayment);

        Payment p2 = Payment.builder()
                .id(502L)
                .lease(testLease)
                .amount(new BigDecimal("1850.00"))
                .lateFee(BigDecimal.ZERO)
                .dueDate(LocalDate.of(2026, 12, 1))
                .status(PaymentStatus.PENDING)
                .paymentMethod("UNPAID")
                .transactionReference("TXN-20261201-99B1C")
                .build();
        yearPayments.add(p2);

        byte[] pdfBytes = pdfGenerationService.generateYearlyStatementPdf(testTenant, 2026, yearPayments);

        assertNotNull(pdfBytes, "Statement PDF should not be null");
        assertTrue(pdfBytes.length > 1500);

        PdfReader reader = new PdfReader(pdfBytes);
        assertTrue(reader.getNumberOfPages() >= 1);
        reader.close();
    }

    @Test
    @DisplayName("Should handle empty payments list gracefully for yearly statement")
    void testGenerateYearlyStatementPdf_EmptyPayments() throws IOException {
        byte[] pdfBytes = pdfGenerationService.generateYearlyStatementPdf(testTenant, 2026, Collections.emptyList());

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000);

        PdfReader reader = new PdfReader(pdfBytes);
        assertTrue(reader.getNumberOfPages() >= 1);
        reader.close();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when null entities are passed")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> pdfGenerationService.generatePaymentReceiptPdf(null));
        assertThrows(IllegalArgumentException.class, () -> pdfGenerationService.generateLeaseAgreementPdf(null));
        assertThrows(IllegalArgumentException.class, () -> pdfGenerationService.generateYearlyStatementPdf(null, 2026, Collections.emptyList()));
    }
}

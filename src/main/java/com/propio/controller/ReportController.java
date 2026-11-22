package com.propio.controller;

import com.propio.dto.ReportSummaryDto;
import com.propio.service.PdfGenerationService;
import com.propio.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final PdfGenerationService pdfGenerationService;

    @GetMapping
    public String showReports(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);
        }

        ReportSummaryDto summary = reportService.generateReportSummary(startDate, endDate);

        model.addAttribute("summary", summary);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("activeNav", "reports");
        model.addAttribute("pageTitle", "Portfolio & Revenue Reports");

        return "reports/index";
    }

    @GetMapping("/export/payments.csv")
    public void exportPaymentsCsv(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"propio_payments_export.csv\"");

        PrintWriter writer = response.getWriter();
        reportService.exportPaymentsCsv(startDate, endDate, writer);
    }

    @GetMapping("/export/tenants.csv")
    public void exportTenantsCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"propio_tenants_export.csv\"");

        PrintWriter writer = response.getWriter();
        reportService.exportTenantsCsv(writer);
    }

    @GetMapping("/export/summary.pdf")
    public ResponseEntity<byte[]> exportSummaryPdf(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);
        }

        ReportSummaryDto summary = reportService.generateReportSummary(startDate, endDate);
        byte[] pdfBytes = pdfGenerationService.generateReportSummaryPdf(startDate, endDate, summary);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"propio_portfolio_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}

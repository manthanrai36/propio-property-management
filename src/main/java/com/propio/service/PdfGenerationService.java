package com.propio.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.propio.model.Lease;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.model.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service generating high-fidelity PDF documents using OpenPDF:
 * 1. Official Payment Receipts with PAID stamp & transaction metadata.
 * 2. Residential Lease Agreements with terms, deposit, late fee clauses, and signature blocks.
 * 3. Annual Resident Financial Statements with full transaction ledger and statistics.
 */
@Service
@Slf4j
public class PdfGenerationService {

    // Brand & Theme Palette
    private static final Color COLOR_PRIMARY = new Color(108, 76, 241);       // Propio Purple #6C4CF1
    private static final Color COLOR_PRIMARY_DARK = new Color(80, 50, 200);
    private static final Color COLOR_DARK = new Color(17, 24, 39);            // #111827
    private static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);   // #64748B
    private static final Color COLOR_BG_LIGHT = new Color(248, 250, 252);     // #F8FAFC
    private static final Color COLOR_BORDER = new Color(226, 232, 240);       // #E2E8F0
    private static final Color COLOR_SUCCESS = new Color(5, 150, 105);        // #059669
    private static final Color COLOR_SUCCESS_BG = new Color(236, 253, 245);   // Emerald 50
    private static final Color COLOR_DANGER = new Color(239, 68, 68);         // #EF4444

    // Typography
    private static final Font FONT_BRAND = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_PRIMARY);
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_DARK);
    private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARY);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_DARK);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_DARK);
    private static final Font FONT_MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXT_MUTED);
    private static final Font FONT_MUTED_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_TEXT_MUTED);
    private static final Font FONT_MONO = FontFactory.getFont(FontFactory.COURIER, 8.5f, COLOR_DARK);
    private static final Font FONT_MONO_BOLD = FontFactory.getFont(FontFactory.COURIER_BOLD, 9, COLOR_DARK);
    private static final Font FONT_STAMP = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_SUCCESS);
    private static final Font FONT_WHITE_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("$#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");

    /**
     * 1. Generates an Official Payment Receipt PDF.
     */
    public byte[] generatePaymentReceiptPdf(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment entity cannot be null for receipt generation.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 45, 45);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PropioPdfPageEvent("Official Rent Payment Receipt"));
            document.open();

            // Brand Header & Document Title
            addHeaderBlock(document, "RENT PAYMENT RECEIPT", payment.getTransactionReference(),
                    payment.getPaidDate() != null ? payment.getPaidDate() : LocalDate.now());

            // Two-column Resident & Property Details
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{50, 50});
            infoTable.setSpacingAfter(15);

            String tenantName = (payment.getLease() != null && payment.getLease().getTenant() != null)
                    ? payment.getLease().getTenant().getFullName() : "Valued Resident";
            String tenantEmail = (payment.getLease() != null && payment.getLease().getTenant() != null)
                    ? payment.getLease().getTenant().getEmail() : "—";
            String tenantPhone = (payment.getLease() != null && payment.getLease().getTenant() != null)
                    ? payment.getLease().getTenant().getPhone() : "—";

            String propName = (payment.getLease() != null && payment.getLease().getUnit() != null && payment.getLease().getUnit().getProperty() != null)
                    ? payment.getLease().getUnit().getProperty().getName() : "Propio Residence";
            String unitNum = (payment.getLease() != null && payment.getLease().getUnit() != null)
                    ? payment.getLease().getUnit().getUnitNumber() : "Unit";
            String propAddress = (payment.getLease() != null && payment.getLease().getUnit() != null && payment.getLease().getUnit().getProperty() != null)
                    ? payment.getLease().getUnit().getProperty().getAddress() + ", " + payment.getLease().getUnit().getProperty().getCity() : "Metropolis";

            PdfPCell leftCell = createCardCell("BILLED TO / RESIDENT",
                    tenantName + "\n" +
                    "Email: " + tenantEmail + "\n" +
                    "Phone: " + tenantPhone);

            PdfPCell rightCell = createCardCell("LEASED PREMISES",
                    propName + " - Unit " + unitNum + "\n" +
                    propAddress + "\n" +
                    "Account Status: ACTIVE RESIDENT");

            infoTable.addCell(leftCell);
            infoTable.addCell(rightCell);
            document.add(infoTable);

            // Itemized Payment Ledger Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{40, 20, 20, 20});
            table.setSpacingAfter(15);

            addTableHeader(table, new String[]{"Description / Billing Item", "Due Date", "Settled Date", "Amount"});

            String desc = payment.getNotes() != null && !payment.getNotes().isBlank()
                    ? payment.getNotes()
                    : "Monthly Rent - " + (payment.getDueDate() != null ? payment.getDueDate().format(MONTH_YEAR_FORMAT) : "Settled");

            String dueDateStr = payment.getDueDate() != null ? payment.getDueDate().format(DATE_FORMAT) : "—";
            String paidDateStr = payment.getPaidDate() != null ? payment.getPaidDate().format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT);

            BigDecimal baseRent = payment.getBaseAmount() != null ? payment.getBaseAmount() : payment.getAmount();
            addTableRow(table, new String[]{desc, dueDateStr, paidDateStr, formatMoney(baseRent)}, false);

            if (payment.getLateFee() != null && payment.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
                addTableRow(table, new String[]{"Late Assessment Penalty Fee", dueDateStr, paidDateStr, formatMoney(payment.getLateFee())}, true);
            }

            // Total Row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL AMOUNT SETTLED:", FONT_BODY_BOLD));
            totalLabelCell.setColspan(3);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(8);
            totalLabelCell.setBackgroundColor(COLOR_BG_LIGHT);
            totalLabelCell.setBorderColor(COLOR_BORDER);
            table.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Phrase(formatMoney(payment.getAmount()), FONT_BODY_BOLD));
            totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValCell.setPadding(8);
            totalValCell.setBackgroundColor(COLOR_BG_LIGHT);
            totalValCell.setBorderColor(COLOR_BORDER);
            table.addCell(totalValCell);

            document.add(table);

            // Payment Metadata Details
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{50, 50});
            metaTable.setSpacingAfter(18);

            String method = payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "ONLINE PORTAL";
            String periodCovered = payment.getDueDate() != null
                    ? payment.getDueDate().withDayOfMonth(1).format(DATE_FORMAT) + " — " +
                      payment.getDueDate().withDayOfMonth(payment.getDueDate().lengthOfMonth()).format(DATE_FORMAT)
                    : "Current Billing Cycle";

            PdfPCell metaLeft = new PdfPCell();
            metaLeft.setBorder(Rectangle.NO_BORDER);
            metaLeft.addElement(new Paragraph("Payment Method: " + method, FONT_BODY));
            metaLeft.addElement(new Paragraph("Period Covered: " + periodCovered, FONT_BODY));
            metaLeft.addElement(new Paragraph("Reference Number: " + payment.getTransactionReference(), FONT_MONO));

            // Green Official "PAID" Stamp Box
            PdfPCell stampCell = new PdfPCell();
            stampCell.setBackgroundColor(COLOR_SUCCESS_BG);
            stampCell.setBorderColor(COLOR_SUCCESS);
            stampCell.setBorderWidth(1.5f);
            stampCell.setPadding(10);
            stampCell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph stampP = new Paragraph("PAID & SETTLED", FONT_STAMP);
            stampP.setAlignment(Element.ALIGN_CENTER);
            Paragraph stampSub = new Paragraph("Transaction Verified • " + paidDateStr, FONT_MUTED);
            stampSub.setAlignment(Element.ALIGN_CENTER);

            stampCell.addElement(stampP);
            stampCell.addElement(stampSub);

            metaTable.addCell(metaLeft);
            metaTable.addCell(stampCell);
            document.add(metaTable);

            // Closing Thank You / Management Note
            Paragraph footerNote = new Paragraph("Thank you for your prompt payment! This digital document serves as official legal proof of rent settlement. For billing questions or accounting inquiries, contact billing@propio.com.", FONT_MUTED);
            footerNote.setAlignment(Element.ALIGN_CENTER);
            footerNote.setSpacingBefore(10);
            document.add(footerNote);

            document.close();
        } catch (DocumentException ex) {
            log.error("Failed to generate payment receipt PDF", ex);
            throw new RuntimeException("Could not generate payment receipt PDF: " + ex.getMessage(), ex);
        }

        return baos.toByteArray();
    }

    /**
     * 2. Generates a Residential Lease Agreement PDF.
     */
    public byte[] generateLeaseAgreementPdf(Lease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease entity cannot be null for agreement generation.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 45, 45);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PropioPdfPageEvent("Residential Tenancy Contract"));
            document.open();

            // Brand Header & Document Title
            String ref = "AGR-LSE-" + String.format("%04d", lease.getId()) + "-" +
                    (lease.getUnit() != null ? lease.getUnit().getUnitNumber() : "00");
            addHeaderBlock(document, "RESIDENTIAL LEASE AGREEMENT", ref, LocalDate.now());

            // Intro Preamble
            Paragraph preamble = new Paragraph(
                    "This Residential Lease Agreement (the 'Agreement') is made and entered into on " +
                    LocalDate.now().format(DATE_FORMAT) + ", by and between the Landlord/Management and the Resident specified below.",
                    FONT_BODY
            );
            preamble.setSpacingAfter(12);
            document.add(preamble);

            // Section 1: Parties
            addSectionHeader(document, "1. PARTIES TO THIS AGREEMENT");
            PdfPTable partiesTable = new PdfPTable(2);
            partiesTable.setWidthPercentage(100);
            partiesTable.setWidths(new float[]{50, 50});
            partiesTable.setSpacingAfter(10);

            PdfPCell landlordCell = createCardCell("LANDLORD / ASSET MANAGER",
                    "Propio Real Estate Management LLC\n" +
                    "Authorized Agent: Manthan (Portfolio Lead)\n" +
                    "Email: leases@propio.com | Phone: (555) 019-2834\n" +
                    "Corporate Office: 100 Propio Blvd, Suite 400");

            String tenantName = lease.getTenant() != null ? lease.getTenant().getFullName() : "Resident";
            String tenantEmail = lease.getTenant() != null ? lease.getTenant().getEmail() : "—";
            String tenantPhone = lease.getTenant() != null ? lease.getTenant().getPhone() : "—";
            String emergency = lease.getTenant() != null && lease.getTenant().getEmergencyContact() != null
                    ? lease.getTenant().getEmergencyContact() : "—";

            PdfPCell tenantCell = createCardCell("RESIDENT / TENANT",
                    tenantName + "\n" +
                    "Email: " + tenantEmail + "\n" +
                    "Phone: " + tenantPhone + "\n" +
                    "Emergency Contact: " + emergency);

            partiesTable.addCell(landlordCell);
            partiesTable.addCell(tenantCell);
            document.add(partiesTable);

            // Section 2: Leased Premises
            addSectionHeader(document, "2. LEASED PREMISES");
            String propName = lease.getUnit() != null && lease.getUnit().getProperty() != null
                    ? lease.getUnit().getProperty().getName() : "Propio Property";
            String unitNum = lease.getUnit() != null ? lease.getUnit().getUnitNumber() : "Unit";
            String address = lease.getUnit() != null && lease.getUnit().getProperty() != null
                    ? lease.getUnit().getProperty().getAddress() + ", " + lease.getUnit().getProperty().getCity() + ", " +
                      lease.getUnit().getProperty().getState() + " " + lease.getUnit().getProperty().getZipCode()
                    : "Address on file";
            String specs = lease.getUnit() != null
                    ? lease.getUnit().getBedrooms() + " Bedroom(s), " + lease.getUnit().getBathrooms() + " Bathroom(s), approx. " +
                      lease.getUnit().getSquareFeet() + " sq ft"
                    : "Standard residential layout";

            Paragraph premisesP = new Paragraph(
                    "The Landlord hereby leases to the Resident, and the Resident hereby leases from the Landlord, the private residential property designated as:\n" +
                    "• Premises: " + propName + ", Unit " + unitNum + "\n" +
                    "• Physical Address: " + address + "\n" +
                    "• Layout & Specifications: " + specs,
                    FONT_BODY
            );
            premisesP.setSpacingAfter(10);
            document.add(premisesP);

            // Section 3: Lease Term
            addSectionHeader(document, "3. TERM OF TENANCY");
            String startDateStr = lease.getStartDate() != null ? lease.getStartDate().format(DATE_FORMAT) : "TBD";
            String endDateStr = lease.getEndDate() != null ? lease.getEndDate().format(DATE_FORMAT) : "TBD";
            int durationMonths = (lease.getStartDate() != null && lease.getEndDate() != null)
                    ? Period.between(lease.getStartDate(), lease.getEndDate()).getMonths() +
                      (Period.between(lease.getStartDate(), lease.getEndDate()).getYears() * 12)
                    : 12;

            Paragraph termP = new Paragraph(
                    "The term of this Lease shall commence on " + startDateStr + " (the 'Commencement Date') and continue until " +
                    endDateStr + " (the 'Expiration Date'), representing a total tenancy period of approximately " + durationMonths +
                    " months. Upon expiration, this Agreement may transition to a month-to-month tenancy or be renewed under mutually agreed terms.",
                    FONT_BODY
            );
            termP.setSpacingAfter(10);
            document.add(termP);

            // Section 4: Rent, Security Deposit & Financial Terms
            addSectionHeader(document, "4. RENT, SECURITY DEPOSIT & PAYMENT RULES");
            PdfPTable financeTable = new PdfPTable(3);
            financeTable.setWidthPercentage(100);
            financeTable.setWidths(new float[]{33, 33, 34});
            financeTable.setSpacingAfter(10);

            financeTable.addCell(createStatCell("Monthly Rent", formatMoney(lease.getMonthlyRent()), "Due 1st of each month"));
            financeTable.addCell(createStatCell("Security Deposit", formatMoney(lease.getSecurityDeposit()), "Refundable under terms"));
            financeTable.addCell(createStatCell("Late Fee Assessment", "5.0%", "Applied after due date"));
            document.add(financeTable);

            Paragraph financeP = new Paragraph(
                    "• Rent Due Date: Monthly rent is strictly due on or before the first (1st) day of each calendar month.\n" +
                    "• Automated Rent Engine: Invoices are generated automatically 30 days prior. Online payments may be settled via the Propio Resident Portal.\n" +
                    "• Late Fee Rule: In accordance with management policy, any rent payment remaining delinquent past the specified due date will incur an automatic 5.0% late penalty fee charged directly to the ledger.\n" +
                    "• Security Deposit: The deposit shall be held in an escrow account and refunded within 30 days of lease termination, subject to property condition inspection.",
                    FONT_BODY
            );
            financeP.setSpacingAfter(14);
            document.add(financeP);

            // Section 5: Legal Signature & Execution Blocks
            addSectionHeader(document, "5. SIGNATURES & ACKNOWLEDGEMENT");
            Paragraph sigNotice = new Paragraph(
                    "IN WITNESS WHEREOF, the parties hereto have executed this Residential Lease Agreement as of the date first written above. By signing below, all parties confirm they have read, understood, and agreed to all provisions contained herein.",
                    FONT_MUTED
            );
            sigNotice.setSpacingAfter(15);
            document.add(sigNotice);

            PdfPTable sigTable = new PdfPTable(2);
            sigTable.setWidthPercentage(100);
            sigTable.setWidths(new float[]{50, 50});
            sigTable.setSpacingAfter(10);

            PdfPCell landlordSig = new PdfPCell();
            landlordSig.setPadding(10);
            landlordSig.setBorderColor(COLOR_BORDER);
            landlordSig.setBackgroundColor(COLOR_BG_LIGHT);
            landlordSig.addElement(new Paragraph("AUTHORIZED MANAGEMENT / LANDLORD:", FONT_MUTED_BOLD));
            landlordSig.addElement(new Paragraph("\n\n____________________________________________", FONT_MUTED));
            landlordSig.addElement(new Paragraph("Propio Real Estate Asset Management", FONT_BODY_BOLD));
            landlordSig.addElement(new Paragraph("Date: " + LocalDate.now().format(DATE_FORMAT), FONT_MUTED));

            PdfPCell tenantSig = new PdfPCell();
            tenantSig.setPadding(10);
            tenantSig.setBorderColor(COLOR_BORDER);
            tenantSig.setBackgroundColor(COLOR_BG_LIGHT);
            tenantSig.addElement(new Paragraph("RESIDENT / TENANT SIGNATURE:", FONT_MUTED_BOLD));
            tenantSig.addElement(new Paragraph("\n\n____________________________________________", FONT_MUTED));
            tenantSig.addElement(new Paragraph(tenantName, FONT_BODY_BOLD));
            tenantSig.addElement(new Paragraph("Date: " + LocalDate.now().format(DATE_FORMAT), FONT_MUTED));

            sigTable.addCell(landlordSig);
            sigTable.addCell(tenantSig);
            document.add(sigTable);

            document.close();
        } catch (DocumentException ex) {
            log.error("Failed to generate lease agreement PDF", ex);
            throw new RuntimeException("Could not generate lease agreement PDF: " + ex.getMessage(), ex);
        }

        return baos.toByteArray();
    }

    /**
     * 3. Generates an Annual Resident Statement PDF for a Tenant profile.
     */
    public byte[] generateYearlyStatementPdf(Tenant tenant, int year, List<Payment> payments) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant entity cannot be null for yearly statement generation.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 45, 45);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PropioPdfPageEvent("Annual Resident Financial Statement"));
            document.open();

            // Brand Header & Document Title
            String ref = "STM-" + year + "-TEN" + String.format("%04d", tenant.getId());
            addHeaderBlock(document, "ANNUAL FINANCIAL STATEMENT - " + year, ref, LocalDate.now());

            // Resident Profile Summary
            PdfPTable residentTable = new PdfPTable(2);
            residentTable.setWidthPercentage(100);
            residentTable.setWidths(new float[]{50, 50});
            residentTable.setSpacingAfter(15);

            String email = tenant.getEmail() != null ? tenant.getEmail() : "—";
            String phone = tenant.getPhone() != null ? tenant.getPhone() : "—";

            PdfPCell resCell = createCardCell("RESIDENT ACCOUNT SUMMARY",
                    tenant.getFullName() + "\n" +
                    "Account ID: PROPIO-RES-" + tenant.getId() + "\n" +
                    "Email: " + email + " | Phone: " + phone);

            PdfPCell periodCell = createCardCell("STATEMENT PERIOD & COVERAGE",
                    "Calendar Year: " + year + "\n" +
                    "Reporting Range: Jan 01, " + year + " — Dec 31, " + year + "\n" +
                    "Generated: " + LocalDate.now().format(DATE_FORMAT) + " | Currency: USD ($)");

            residentTable.addCell(resCell);
            residentTable.addCell(periodCell);
            document.add(residentTable);

            // Compute Financial Metrics for the Statement Period
            BigDecimal totalInvoiced = BigDecimal.ZERO;
            BigDecimal totalPaid = BigDecimal.ZERO;
            BigDecimal totalOverdue = BigDecimal.ZERO;
            BigDecimal totalLateFees = BigDecimal.ZERO;

            if (payments != null) {
                for (Payment p : payments) {
                    BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                    totalInvoiced = totalInvoiced.add(amt);
                    if (p.getStatus() == PaymentStatus.PAID) {
                        totalPaid = totalPaid.add(amt);
                    } else if (p.getStatus() == PaymentStatus.OVERDUE) {
                        totalOverdue = totalOverdue.add(amt);
                    }
                    if (p.getLateFee() != null) {
                        totalLateFees = totalLateFees.add(p.getLateFee());
                    }
                }
            }

            // Summary Stats Cards (4 stats)
            PdfPTable statsTable = new PdfPTable(4);
            statsTable.setWidthPercentage(100);
            statsTable.setWidths(new float[]{25, 25, 25, 25});
            statsTable.setSpacingAfter(15);

            statsTable.addCell(createStatCell("Total Invoiced", formatMoney(totalInvoiced), "Year " + year));
            statsTable.addCell(createStatCell("Total Paid", formatMoney(totalPaid), "Settled dues"));
            statsTable.addCell(createStatCell("Total Overdue", formatMoney(totalOverdue), "Delinquent"));
            statsTable.addCell(createStatCell("Late Fees Incurred", formatMoney(totalLateFees), "5% penalty charges"));
            document.add(statsTable);

            // Chronological Ledger Table
            addSectionHeader(document, "TRANSACTIONS LEDGER");
            PdfPTable ledgerTable = new PdfPTable(6);
            ledgerTable.setWidthPercentage(100);
            ledgerTable.setWidths(new float[]{22, 16, 16, 20, 14, 12});
            ledgerTable.setSpacingAfter(15);

            addTableHeader(ledgerTable, new String[]{"Txn Reference", "Due Date", "Paid Date", "Payment Method", "Amount", "Status"});

            if (payments == null || payments.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No payment transactions recorded during the " + year + " reporting period.", FONT_MUTED));
                emptyCell.setColspan(6);
                emptyCell.setPadding(12);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                ledgerTable.addCell(emptyCell);
            } else {
                for (Payment p : payments) {
                    String refStr = p.getTransactionReference() != null ? p.getTransactionReference() : "—";
                    String dueStr = p.getDueDate() != null ? p.getDueDate().format(DATE_FORMAT) : "—";
                    String paidStr = p.getPaidDate() != null ? p.getPaidDate().format(DATE_FORMAT) : "—";
                    String methodStr = p.getPaymentMethod() != null ? p.getPaymentMethod() : "UNPAID";
                    String amtStr = formatMoney(p.getAmount());
                    String statusStr = p.getStatus() != null ? p.getStatus().name() : "PENDING";

                    addLedgerRow(ledgerTable, refStr, dueStr, paidStr, methodStr, amtStr, statusStr);
                }

                // Ledger Totals Row
                PdfPCell labelCell = new PdfPCell(new Phrase("TOTAL ACCUMULATED:", FONT_BODY_BOLD));
                labelCell.setColspan(4);
                labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                labelCell.setPadding(7);
                labelCell.setBackgroundColor(COLOR_BG_LIGHT);
                labelCell.setBorderColor(COLOR_BORDER);
                ledgerTable.addCell(labelCell);

                PdfPCell valCell = new PdfPCell(new Phrase(formatMoney(totalInvoiced), FONT_BODY_BOLD));
                valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                valCell.setPadding(7);
                valCell.setBackgroundColor(COLOR_BG_LIGHT);
                valCell.setBorderColor(COLOR_BORDER);
                ledgerTable.addCell(valCell);

                PdfPCell blankCell = new PdfPCell(new Phrase("", FONT_BODY));
                blankCell.setBackgroundColor(COLOR_BG_LIGHT);
                blankCell.setBorderColor(COLOR_BORDER);
                ledgerTable.addCell(blankCell);
            }

            document.add(ledgerTable);

            // Closing Sign-off
            Paragraph certP = new Paragraph(
                    "Certification: This statement reflects the official financial ledger recorded in the Propio SaaS management system for the specified year. All figures are verified and legally recognized by Propio Residential Services.",
                    FONT_MUTED
            );
            certP.setSpacingBefore(10);
            document.add(certP);

            document.close();
        } catch (DocumentException ex) {
            log.error("Failed to generate yearly statement PDF", ex);
            throw new RuntimeException("Could not generate yearly statement PDF: " + ex.getMessage(), ex);
        }

        return baos.toByteArray();
    }

    // =========================================================================
    // Reusable PDF Helper Components
    // =========================================================================

    private void addHeaderBlock(Document doc, String docTitle, String refCode, LocalDate issueDate) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{55, 45});
        headerTable.setSpacingAfter(8);

        // Left brand cell
        PdfPCell brandCell = new PdfPCell();
        brandCell.setBorder(Rectangle.NO_BORDER);
        Paragraph brandP = new Paragraph("PROPIO", FONT_BRAND);
        Paragraph brandSub = new Paragraph("PREMIUM RESIDENTIAL MANAGEMENT SAAS", FONT_MUTED_BOLD);
        brandCell.addElement(brandP);
        brandCell.addElement(brandSub);

        // Right title cell
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph titleP = new Paragraph(docTitle, FONT_TITLE);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        Paragraph refP = new Paragraph("Ref: " + (refCode != null ? refCode : "N/A"), FONT_MONO_BOLD);
        refP.setAlignment(Element.ALIGN_RIGHT);
        Paragraph dateP = new Paragraph("Issued: " + issueDate.format(DATE_FORMAT), FONT_MUTED);
        dateP.setAlignment(Element.ALIGN_RIGHT);

        titleCell.addElement(titleP);
        titleCell.addElement(refP);
        titleCell.addElement(dateP);

        headerTable.addCell(brandCell);
        headerTable.addCell(titleCell);
        doc.add(headerTable);

        // Stylish accent divider bar
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell divCell = new PdfPCell();
        divCell.setBackgroundColor(COLOR_PRIMARY);
        divCell.setFixedHeight(2.5f);
        divCell.setBorder(Rectangle.NO_BORDER);
        divider.addCell(divCell);
        divider.setSpacingAfter(14);
        doc.add(divider);
    }

    private void addSectionHeader(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, FONT_SECTION);
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private PdfPCell createCardCell(String header, String content) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(8);

        Paragraph hP = new Paragraph(header, FONT_MUTED_BOLD);
        hP.setSpacingAfter(4);
        cell.addElement(hP);

        Paragraph cP = new Paragraph(content, FONT_BODY);
        cell.addElement(cP);
        return cell;
    }

    private PdfPCell createStatCell(String title, String mainValue, String sub) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph tP = new Paragraph(title, FONT_MUTED);
        tP.setAlignment(Element.ALIGN_CENTER);
        Paragraph vP = new Paragraph(mainValue, FONT_SECTION);
        vP.setAlignment(Element.ALIGN_CENTER);
        Paragraph sP = new Paragraph(sub, FONT_MUTED);
        sP.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(tP);
        cell.addElement(vP);
        cell.addElement(sP);
        return cell;
    }

    private void addTableHeader(PdfPTable table, String[] headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_BODY_BOLD));
            cell.setBackgroundColor(COLOR_BG_LIGHT);
            cell.setBorderColor(COLOR_BORDER);
            cell.setPadding(6);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String[] values, boolean isHighlight) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(values[i], isHighlight ? FONT_BODY_BOLD : FONT_BODY));
            cell.setPadding(6);
            cell.setBorderColor(COLOR_BORDER);
            if (i == values.length - 1) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            if (isHighlight) {
                cell.setBackgroundColor(new Color(254, 242, 242)); // light red
            }
            table.addCell(cell);
        }
    }

    private void addLedgerRow(PdfPTable table, String ref, String due, String paid, String method, String amt, String status) {
        PdfPCell c1 = new PdfPCell(new Phrase(ref, FONT_MONO));
        c1.setPadding(5);
        c1.setBorderColor(COLOR_BORDER);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(due, FONT_BODY));
        c2.setPadding(5);
        c2.setBorderColor(COLOR_BORDER);
        table.addCell(c2);

        PdfPCell c3 = new PdfPCell(new Phrase(paid, FONT_BODY));
        c3.setPadding(5);
        c3.setBorderColor(COLOR_BORDER);
        table.addCell(c3);

        PdfPCell c4 = new PdfPCell(new Phrase(method, FONT_MUTED));
        c4.setPadding(5);
        c4.setBorderColor(COLOR_BORDER);
        table.addCell(c4);

        PdfPCell c5 = new PdfPCell(new Phrase(amt, FONT_BODY_BOLD));
        c5.setPadding(5);
        c5.setBorderColor(COLOR_BORDER);
        c5.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c5);

        // Status pill
        Color statusColor = "PAID".equalsIgnoreCase(status) ? COLOR_SUCCESS :
                            "OVERDUE".equalsIgnoreCase(status) ? COLOR_DANGER : COLOR_PRIMARY;
        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, statusColor);
        PdfPCell c6 = new PdfPCell(new Phrase(status, statusFont));
        c6.setPadding(5);
        c6.setBorderColor(COLOR_BORDER);
        c6.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(c6);
    }

    private String formatMoney(BigDecimal amount) {
        return MONEY_FORMAT.format(amount != null ? amount : BigDecimal.ZERO);
    }

    /**
     * Page Event helper to render page footers with page numbers on every page.
     */
    private static class PropioPdfPageEvent extends PdfPageEventHelper {
        private final String documentType;

        public PropioPdfPageEvent(String documentType) {
            this.documentType = documentType;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float pageBottom = document.bottom() - 15;
            float left = document.left();
            float right = document.right();

            // Subtle divider rule
            cb.setColorStroke(COLOR_BORDER);
            cb.setLineWidth(0.5f);
            cb.moveTo(left, pageBottom + 12);
            cb.lineTo(right, pageBottom + 12);
            cb.stroke();

            // Footer text
            String leftText = "Propio Residential Management • " + documentType;
            String rightText = "Page " + writer.getPageNumber() + " • Confidential";

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(leftText, FONT_MUTED), left, pageBottom, 0);

            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase(rightText, FONT_MUTED), right, pageBottom, 0);
        }
    }
}

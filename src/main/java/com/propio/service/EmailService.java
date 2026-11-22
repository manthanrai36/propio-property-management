package com.propio.service;

import com.propio.model.*;
import com.propio.repository.EmailLogRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing all automated and manual email notifications in Propio.
 * - Dispatches emails asynchronously via @Async to guarantee zero UI latency.
 * - Fallback simulation: If SMTP is disabled (propio.mail.enabled=false), cleanly logs formatted email content.
 * - Robust error handling with up to 3 automatic retries for transient mail server drops.
 * - Prevents duplicate reminders via EmailLogRepository check.
 */
@Service
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;
    private final PdfGenerationService pdfGenerationService;

    @Value("${propio.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${propio.mail.from-address:noreply@propio.com}")
    private String fromAddress;

    @Value("${propio.mail.from-name:Propio Property Management}")
    private String fromName;

    @Value("${propio.mail.retry-max-attempts:3}")
    private int maxRetryAttempts;

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("$#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            SpringTemplateEngine templateEngine,
            EmailLogRepository emailLogRepository,
            PdfGenerationService pdfGenerationService) {
        this.mailSenderProvider = mailSenderProvider;
        this.templateEngine = templateEngine;
        this.emailLogRepository = emailLogRepository;
        this.pdfGenerationService = pdfGenerationService;
    }

    public boolean isMailEnabled() {
        return mailEnabled;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    // =========================================================================
    // Automated Event Dispatchers
    // =========================================================================

    /**
     * Dispatches reminder for rent due in 3 days.
     */
    public void sendRentDueSoonEmail(Payment payment) {
        if (payment == null || payment.getLease() == null || payment.getLease().getTenant() == null) {
            return;
        }

        // Duplicate protection check
        if (emailLogRepository.hasSentEmail(EmailType.RENT_DUE_SOON, payment.getId())) {
            log.debug("Rent due soon email already sent for payment #{}", payment.getId());
            return;
        }

        Tenant tenant = payment.getLease().getTenant();
        Unit unit = payment.getLease().getUnit();
        Property property = unit != null ? unit.getProperty() : null;

        String recipient = tenant.getEmail();
        if (recipient == null || recipient.isBlank()) return;

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("unitNumber", unit != null ? unit.getUnitNumber() : "Unit");
        context.setVariable("propertyName", property != null ? property.getName() : "Property");
        context.setVariable("dueDate", payment.getDueDate() != null ? payment.getDueDate().format(DATE_FORMAT) : "");
        context.setVariable("amount", CURRENCY_FORMAT.format(payment.getAmount()));
        context.setVariable("portalUrl", "/payments");

        String htmlBody = templateEngine.process("emails/rent_due_soon", context);
        String subject = "Upcoming Rent Reminder - Unit " + (unit != null ? unit.getUnitNumber() : "") + " Due Soon";

        dispatchAsync(recipient, subject, EmailType.RENT_DUE_SOON, htmlBody, payment.getId(), null, null);
    }

    /**
     * Dispatches notice for overdue rent.
     */
    public void sendRentOverdueEmail(Payment payment) {
        if (payment == null || payment.getLease() == null || payment.getLease().getTenant() == null) {
            return;
        }

        // Duplicate protection check
        if (emailLogRepository.hasSentEmail(EmailType.RENT_OVERDUE, payment.getId())) {
            log.debug("Rent overdue email already sent for payment #{}", payment.getId());
            return;
        }

        Tenant tenant = payment.getLease().getTenant();
        Unit unit = payment.getLease().getUnit();
        Property property = unit != null ? unit.getProperty() : null;

        String recipient = tenant.getEmail();
        if (recipient == null || recipient.isBlank()) return;

        BigDecimal lateFee = payment.getLateFee() != null ? payment.getLateFee() : BigDecimal.ZERO;
        BigDecimal totalDue = payment.getAmount().add(lateFee);

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("unitNumber", unit != null ? unit.getUnitNumber() : "Unit");
        context.setVariable("propertyName", property != null ? property.getName() : "Property");
        context.setVariable("dueDate", payment.getDueDate() != null ? payment.getDueDate().format(DATE_FORMAT) : "");
        context.setVariable("amount", CURRENCY_FORMAT.format(payment.getAmount()));
        context.setVariable("lateFee", CURRENCY_FORMAT.format(lateFee));
        context.setVariable("totalDue", CURRENCY_FORMAT.format(totalDue));
        context.setVariable("portalUrl", "/payments");

        String htmlBody = templateEngine.process("emails/rent_overdue", context);
        String subject = "OVERDUE NOTICE: Immediate Payment Required for Unit " + (unit != null ? unit.getUnitNumber() : "");

        dispatchAsync(recipient, subject, EmailType.RENT_OVERDUE, htmlBody, payment.getId(), null, null);
    }

    /**
     * Dispatches payment confirmation with official receipt PDF attached.
     */
    public void sendPaymentReceivedEmail(Payment payment) {
        if (payment == null || payment.getLease() == null || payment.getLease().getTenant() == null) {
            return;
        }

        if (emailLogRepository.hasSentEmail(EmailType.PAYMENT_RECEIVED, payment.getId())) {
            log.debug("Payment confirmation email already sent for payment #{}", payment.getId());
            return;
        }

        Tenant tenant = payment.getLease().getTenant();
        Unit unit = payment.getLease().getUnit();
        Property property = unit != null ? unit.getProperty() : null;

        String recipient = tenant.getEmail();
        if (recipient == null || recipient.isBlank()) return;

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("unitNumber", unit != null ? unit.getUnitNumber() : "Unit");
        context.setVariable("propertyName", property != null ? property.getName() : "Property");
        context.setVariable("paidDate", payment.getPaidDate() != null ? payment.getPaidDate().format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT));
        context.setVariable("amount", CURRENCY_FORMAT.format(payment.getAmount()));
        context.setVariable("paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "Card");
        context.setVariable("transactionRef", payment.getTransactionReference() != null ? payment.getTransactionReference() : "TXN-N/A");
        context.setVariable("portalUrl", "/payments/my-payments");

        String htmlBody = templateEngine.process("emails/payment_received", context);
        String subject = "Payment Confirmation & Receipt - " + payment.getTransactionReference();

        // Generate receipt PDF attachment
        byte[] pdfBytes = null;
        String attachmentName = "Receipt-" + payment.getTransactionReference() + ".pdf";
        try {
            pdfBytes = pdfGenerationService.generatePaymentReceiptPdf(payment);
        } catch (Exception ex) {
            log.error("Failed to generate PDF receipt for payment {}: {}", payment.getId(), ex.getMessage());
        }

        dispatchAsync(recipient, subject, EmailType.PAYMENT_RECEIVED, htmlBody, payment.getId(), pdfBytes, attachmentName);
    }

    /**
     * Dispatches notification for lease expiring in 60 days.
     */
    public void sendLeaseExpiringEmail(Lease lease, long daysRemaining) {
        if (lease == null || lease.getTenant() == null) {
            return;
        }

        if (emailLogRepository.hasSentEmail(EmailType.LEASE_EXPIRING, lease.getId())) {
            log.debug("Lease expiring email already sent for lease #{}", lease.getId());
            return;
        }

        Tenant tenant = lease.getTenant();
        Unit unit = lease.getUnit();
        Property property = unit != null ? unit.getProperty() : null;

        String recipient = tenant.getEmail();
        if (recipient == null || recipient.isBlank()) return;

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("unitNumber", unit != null ? unit.getUnitNumber() : "Unit");
        context.setVariable("propertyName", property != null ? property.getName() : "Property");
        context.setVariable("endDate", lease.getEndDate() != null ? lease.getEndDate().format(DATE_FORMAT) : "");
        context.setVariable("daysRemaining", daysRemaining);
        context.setVariable("portalUrl", "/leasing");

        String htmlBody = templateEngine.process("emails/lease_expiring", context);
        String subject = "Lease Expiration Notice (" + daysRemaining + " Days) - Unit " + (unit != null ? unit.getUnitNumber() : "");

        dispatchAsync(recipient, subject, EmailType.LEASE_EXPIRING, htmlBody, lease.getId(), null, null);
    }

    /**
     * Dispatches status update notification for maintenance tickets.
     */
    public void sendMaintenanceUpdateEmail(MaintenanceRequest request, String updateNotes) {
        if (request == null || request.getTenant() == null) {
            return;
        }

        Tenant tenant = request.getTenant();
        Unit unit = request.getUnit();
        Property property = unit != null ? unit.getProperty() : null;

        String recipient = tenant.getEmail();
        if (recipient == null || recipient.isBlank()) return;

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("requestTitle", request.getTitle());
        context.setVariable("ticketId", "REQ-" + request.getId());
        context.setVariable("unitNumber", unit != null ? unit.getUnitNumber() : "Unit");
        context.setVariable("propertyName", property != null ? property.getName() : "Property");
        context.setVariable("category", request.getCategory() != null ? request.getCategory() : "General");
        context.setVariable("priority", request.getPriority() != null ? request.getPriority() : "NORMAL");
        context.setVariable("status", request.getStatus() != null ? request.getStatus().name() : "UPDATED");
        context.setVariable("updateDate", LocalDate.now().format(DATE_FORMAT));
        context.setVariable("notes", updateNotes != null ? updateNotes : "");
        context.setVariable("portalUrl", "/maintenance/" + request.getId());

        String htmlBody = templateEngine.process("emails/maintenance_update", context);
        String subject = "Maintenance Update: " + request.getTitle() + " [" + request.getStatus() + "]";

        dispatchAsync(recipient, subject, EmailType.MAINTENANCE_UPDATE, htmlBody, request.getId(), null, null);
    }

    /**
     * Dispatches onboarding email with temporary password for new tenant registrations.
     */
    public void sendWelcomeEmail(Tenant tenant, String temporaryPassword) {
        if (tenant == null || tenant.getEmail() == null || tenant.getEmail().isBlank()) {
            return;
        }

        Context context = new Context();
        context.setVariable("tenantName", tenant.getFullName());
        context.setVariable("tenantEmail", tenant.getEmail());
        context.setVariable("temporaryPassword", temporaryPassword != null ? temporaryPassword : "AutoGeneratedPassword123!");
        context.setVariable("portalUrl", "/login");

        String htmlBody = templateEngine.process("emails/welcome_tenant", context);
        String subject = "Welcome to Propio - Resident Portal Access Details";

        dispatchAsync(tenant.getEmail(), subject, EmailType.WELCOME_TENANT, htmlBody, tenant.getId(), null, null);
    }

    /**
     * Sends manual test diagnostic email from the admin portal.
     */
    public void sendTestEmail(String recipient, String customMessage) {
        Context context = new Context();
        context.setVariable("recipient", recipient);
        context.setVariable("fromAddress", fromAddress);
        context.setVariable("dispatchMode", mailEnabled ? "LIVE SMTP SERVER" : "SIMULATED (LOCAL LOG FALLBACK)");
        context.setVariable("timestamp", LocalDateTime.now().format(DATETIME_FORMAT));
        context.setVariable("customMessage", customMessage != null ? customMessage : "Manual system test initiated from Propio Admin Console.");

        String htmlBody = templateEngine.process("emails/test_email", context);
        String subject = "Propio System Diagnostic: Test Email Dispatch";

        dispatchAsync(recipient, subject, EmailType.TEST_EMAIL, htmlBody, null, null, null);
    }

    /**
     * Resends an existing email log.
     */
    public void resendEmail(Long logId) {
        EmailLog existingLog = emailLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Email log #" + logId + " not found"));

        dispatchAsync(
                existingLog.getRecipient(),
                "RESEND: " + existingLog.getSubject(),
                existingLog.getEmailType(),
                existingLog.getHtmlBody(),
                existingLog.getReferenceId(),
                null,
                existingLog.getAttachmentName()
        );
    }

    // =========================================================================
    // Core Asynchronous Dispatch Pipeline with Retries & Fallback
    // =========================================================================

    @Async("mailTaskExecutor")
    public CompletableFuture<EmailLog> dispatchAsync(
            String recipient,
            String subject,
            EmailType emailType,
            String htmlBody,
            Long referenceId,
            byte[] attachmentBytes,
            String attachmentName) {

        EmailLog emailLog = EmailLog.builder()
                .recipient(recipient)
                .subject(subject)
                .emailType(emailType)
                .htmlBody(htmlBody)
                .referenceId(referenceId)
                .hasAttachment(attachmentBytes != null && attachmentBytes.length > 0)
                .attachmentName(attachmentName)
                .retryCount(0)
                .status(EmailStatus.RETRYING)
                .build();

        emailLog = emailLogRepository.save(emailLog);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        // Safe fallback when SMTP is disabled or not configured
        if (!mailEnabled || mailSender == null) {
            log.info("\n" +
                    "================================================================================\n" +
                    " [PROPIO EMAIL DISPATCH - LOCAL SAFE FALLBACK]\n" +
                    "================================================================================\n" +
                    " Recipient:    {}\n" +
                    " Subject:      {}\n" +
                    " Email Type:   {}\n" +
                    " Reference ID: {}\n" +
                    " Attachment:   {}\n" +
                    " Status:       SIMULATED_SENT\n" +
                    " Timestamp:    {}\n" +
                    "--------------------------------------------------------------------------------\n" +
                    " Email Content Rendered Successfully ({} characters HTML)\n" +
                    " Safe Mode: SMTP delivery disabled. Set propio.mail.enabled=true to send via SMTP.\n" +
                    "================================================================================",
                    recipient, subject, emailType, referenceId,
                    (emailLog.isHasAttachment() ? attachmentName : "None"),
                    LocalDateTime.now().format(DATETIME_FORMAT),
                    htmlBody != null ? htmlBody.length() : 0);

            emailLog.setStatus(EmailStatus.SIMULATED_SENT);
            emailLog.setSentAt(LocalDateTime.now());
            emailLog = emailLogRepository.save(emailLog);
            return CompletableFuture.completedFuture(emailLog);
        }

        // Live SMTP Delivery with Retry Loop
        boolean sentSuccessfully = false;
        String lastErrorMessage = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            emailLog.setRetryCount(attempt - 1);
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        mimeMessage,
                        emailLog.isHasAttachment(),
                        StandardCharsets.UTF_8.name());

                helper.setFrom(new InternetAddress(fromAddress, fromName));
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                if (emailLog.isHasAttachment() && attachmentBytes != null) {
                    helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes));
                }

                mailSender.send(mimeMessage);
                sentSuccessfully = true;
                log.info("Live SMTP email successfully dispatched to {} (Subject: {})", recipient, subject);
                break;

            } catch (Exception ex) {
                lastErrorMessage = ex.getMessage();
                log.warn("SMTP send attempt {} of {} failed for {}: {}", attempt, maxRetryAttempts, recipient, ex.getMessage());
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(1000L * attempt); // Backoff between retries
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (sentSuccessfully) {
            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            emailLog.setErrorMessage(null);
        } else {
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage(lastErrorMessage);
            log.error("Failed to deliver email to {} after {} attempts: {}", recipient, maxRetryAttempts, lastErrorMessage);
        }

        emailLog = emailLogRepository.save(emailLog);
        return CompletableFuture.completedFuture(emailLog);
    }
}

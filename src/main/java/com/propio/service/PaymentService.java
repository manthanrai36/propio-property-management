package com.propio.service;

import com.propio.dto.*;
import com.propio.exception.PaymentFailedException;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.Lease;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.repository.LeaseRepository;
import com.propio.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service managing payment operations, recording receipts, tenant checkout,
 * and the automated rent engine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LeaseRepository leaseRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;

    @Value("${propio.billing.late-fee-percentage:5.0}")
    private double lateFeePercentage;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Transactional(readOnly = true)
    public Page<Payment> getAdminPaymentsPaged(PaymentFilterDto filter) {
        int pageNum = Math.max(0, filter.getPage());
        int pageSize = filter.getSize() > 0 ? filter.getSize() : 10;
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        PaymentStatus status = null;
        if (filter.getStatus() != null && !filter.getStatus().isBlank() && !"ALL".equalsIgnoreCase(filter.getStatus())) {
            try {
                status = PaymentStatus.valueOf(filter.getStatus().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String search = (filter.getSearch() != null && !filter.getSearch().isBlank())
                ? filter.getSearch().trim() : null;

        return paymentRepository.searchPaymentsWithDetails(
                status, filter.getStartDate(), filter.getEndDate(), search, pageable
        );
    }

    @Transactional(readOnly = true)
    public PaymentTotalsDto getPaymentTotals() {
        BigDecimal collected = paymentRepository.sumAmountByStatus(PaymentStatus.PAID);
        BigDecimal pending = paymentRepository.sumAmountByStatus(PaymentStatus.PENDING);
        BigDecimal overdue = paymentRepository.sumAmountByStatus(PaymentStatus.OVERDUE);
        BigDecimal invoiced = paymentRepository.sumTotalAmount();

        long countPaid = paymentRepository.countByStatus(PaymentStatus.PAID);
        long countPending = paymentRepository.countByStatus(PaymentStatus.PENDING);
        long countOverdue = paymentRepository.countByStatus(PaymentStatus.OVERDUE);
        long countTotal = paymentRepository.count();

        return PaymentTotalsDto.builder()
                .totalCollected(collected != null ? collected : BigDecimal.ZERO)
                .totalPending(pending != null ? pending : BigDecimal.ZERO)
                .totalOverdue(overdue != null ? overdue : BigDecimal.ZERO)
                .totalInvoiced(invoiced != null ? invoiced : BigDecimal.ZERO)
                .countPaid(countPaid)
                .countPending(countPending)
                .countOverdue(countOverdue)
                .countTotal(countTotal)
                .build();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        return paymentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment invoice not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByTenantId(Long tenantId) {
        return paymentRepository.findAllByTenantIdWithFullDetails(tenantId);
    }

    @Transactional
    public Payment recordPayment(RecordPaymentDto dto) {
        Payment payment = getPaymentById(dto.getPaymentId());

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Invoice '" + payment.getTransactionReference() + "' is already settled and marked as PAID.");
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidDate(dto.getPaidDate() != null ? dto.getPaidDate() : LocalDate.now());
        payment.setPaymentMethod(dto.getPaymentMethod().trim().toUpperCase());

        String recordMemo = "Recorded by Management (" + payment.getPaymentMethod() + ") on " + LocalDate.now();
        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            recordMemo += " - " + dto.getNotes().trim();
        }

        payment.setNotes(payment.getNotes() != null ? payment.getNotes() + " | " + recordMemo : recordMemo);
        Payment saved = paymentRepository.save(payment);
        log.info("Recorded manual payment for Invoice ID {}: Amount ${}, Method {}",
                saved.getId(), saved.getAmount(), saved.getPaymentMethod());
        
        // Dispatch payment receipt email asynchronously with PDF attachment
        try {
            emailService.sendPaymentReceivedEmail(saved);
        } catch (Exception ex) {
            log.warn("Failed to queue payment receipt email for payment #{}: {}", saved.getId(), ex.getMessage());
        }

        // Generate in-app notifications
        try {
            if (saved.getLease() != null && saved.getLease().getTenant() != null && saved.getLease().getTenant().getUser() != null) {
                notificationService.createNotification(
                        saved.getLease().getTenant().getUser(),
                        "Payment Confirmed",
                        "Payment of $" + saved.getAmount() + " for " + saved.getTransactionReference() + " was recorded.",
                        com.propio.model.NotificationType.PAYMENT_RECEIVED,
                        "/payments"
                );
            }
            notificationService.notifyAllAdmins(
                    "Payment Recorded",
                    "Payment of $" + saved.getAmount() + " recorded for " + (saved.getLease() != null && saved.getLease().getTenant() != null ? saved.getLease().getTenant().getFullName() : "Tenant"),
                    com.propio.model.NotificationType.PAYMENT_RECEIVED,
                    "/payments"
            );
        } catch (Exception ex) {
            log.warn("Failed creating notification for payment #{}: {}", saved.getId(), ex.getMessage());
        }

        return saved;
    }

    @Transactional
    public Payment processOnlinePayment(Long paymentId, SimulatedCheckoutDto dto) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Invoice '" + payment.getTransactionReference() + "' is already paid.");
        }

        // Simulate Gateway failure scenario
        if ("FAILURE".equalsIgnoreCase(dto.getSimulationOutcome())) {
            log.warn("Simulated payment gateway declined transaction for Invoice ID: {}", paymentId);
            throw new PaymentFailedException(
                    "Simulated Gateway Error [Declined - Code 204]: Transaction declined by issuing bank (Insufficient funds or verification error). Your invoice remains unpaid.",
                    paymentId
            );
        }

        // Simulate Gateway success scenario
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidDate(LocalDate.now());
        payment.setPaymentMethod("CARD (Online)");

        String last4 = (dto.getCardNumberMasked() != null && dto.getCardNumberMasked().length() >= 4)
                ? dto.getCardNumberMasked().substring(dto.getCardNumberMasked().length() - 4) : "8899";
        String authRef = "AUTH-" + generateRandomAlphanumeric(6);

        String successMemo = String.format("Online card payment approved [Auth: %s, Card: Visa **** %s, Date: %s]",
                authRef, last4, LocalDate.now());
        payment.setNotes(payment.getNotes() != null ? payment.getNotes() + " | " + successMemo : successMemo);

        Payment saved = paymentRepository.save(payment);
        log.info("Successfully processed online payment for Invoice ID {} ({}) for Tenant: {}",
                saved.getId(), saved.getTransactionReference(), saved.getLease().getTenant().getFullName());
        
        // Dispatch payment receipt email asynchronously with PDF attachment
        try {
            emailService.sendPaymentReceivedEmail(saved);
        } catch (Exception ex) {
            log.warn("Failed to queue online payment receipt email for payment #{}: {}", saved.getId(), ex.getMessage());
        }

        // Generate in-app notifications
        try {
            if (saved.getLease() != null && saved.getLease().getTenant() != null && saved.getLease().getTenant().getUser() != null) {
                notificationService.createNotification(
                        saved.getLease().getTenant().getUser(),
                        "Payment Successful",
                        "Your online payment of $" + saved.getAmount() + " (" + saved.getTransactionReference() + ") was approved.",
                        com.propio.model.NotificationType.PAYMENT_RECEIVED,
                        "/payments"
                );
            }
            notificationService.notifyAllAdmins(
                    "Online Payment Received",
                    "Online payment of $" + saved.getAmount() + " settled by " + (saved.getLease() != null && saved.getLease().getTenant() != null ? saved.getLease().getTenant().getFullName() : "Tenant"),
                    com.propio.model.NotificationType.PAYMENT_RECEIVED,
                    "/payments"
            );
        } catch (Exception ex) {
            log.warn("Failed creating notification for online payment #{}: {}", saved.getId(), ex.getMessage());
        }

        return saved;
    }

    /**
     * Automated Rent Engine Billing Cycle:
     * 1. Generates next month's PENDING rent invoice for every active lease (with strict duplicate protection).
     * 2. Identifies invoices past due date that are PENDING, transitions them to OVERDUE, and assesses configurable late fee.
     * 3. Emits clean summary log line each execution.
     */
    @Transactional
    public BillingCycleResult runBillingCycle(String triggerSource) {
        log.info("[Rent Engine] [{}] Commencing automated billing run...", triggerSource);

        int generatedCount = 0;
        int overdueCount = 0;
        int lateFeesCount = 0;
        int skippedDuplicatesCount = 0;
        BigDecimal totalLateFeesAssessed = BigDecimal.ZERO;

        // --- 1. Generate Next Month's PENDING Invoices for Active Leases ---
        List<Lease> activeLeases = leaseRepository.findByActiveTrue();
        LocalDate nextMonth = LocalDate.now().plusMonths(1);

        for (Lease lease : activeLeases) {
            if (lease.getUnit() == null) continue;

            int dueDay = (lease.getStartDate() != null) ? lease.getStartDate().getDayOfMonth() : 1;
            int maxDaysInNextMonth = nextMonth.lengthOfMonth();
            LocalDate targetDueDate = nextMonth.withDayOfMonth(Math.min(dueDay, maxDaysInNextMonth));

            // Invariant check: do not bill beyond lease end date
            if (lease.getEndDate() != null && targetDueDate.isAfter(lease.getEndDate())) {
                continue;
            }

            // DUPLICATE INVOICE PROTECTION:
            // Ensure no invoice has already been generated for this lease in the target billing month
            LocalDate monthStart = targetDueDate.withDayOfMonth(1);
            LocalDate monthEnd = targetDueDate.withDayOfMonth(targetDueDate.lengthOfMonth());
            boolean alreadyInvoiced = paymentRepository.existsByLeaseIdAndDueDateBetween(lease.getId(), monthStart, monthEnd);

            if (!alreadyInvoiced) {
                String propName = (lease.getUnit().getProperty() != null && lease.getUnit().getProperty().getName() != null)
                        ? lease.getUnit().getProperty().getName() : "Propio Residence";
                String unitNum = (lease.getUnit().getUnitNumber() != null) ? lease.getUnit().getUnitNumber() : "Unit";

                String ref = generateUniqueTransactionReference(targetDueDate);
                String notes = String.format("Monthly rent for %s %d - %s (%s)",
                        targetDueDate.getMonth().name(), targetDueDate.getYear(), propName, unitNum);

                Payment invoice = Payment.builder()
                        .lease(lease)
                        .amount(lease.getMonthlyRent())
                        .lateFee(BigDecimal.ZERO)
                        .dueDate(targetDueDate)
                        .status(PaymentStatus.PENDING)
                        .paymentMethod("UNPAID")
                        .transactionReference(ref)
                        .notes(notes)
                        .build();

                paymentRepository.save(invoice);
                generatedCount++;
            } else {
                skippedDuplicatesCount++;
            }
        }

        // Fetch dynamic late fee percentage configured via Admin Settings
        BigDecimal dynamicLateFeePct = systemSettingService != null ? systemSettingService.getLateFeePercentage() : BigDecimal.valueOf(lateFeePercentage);

        // --- 2. Mark Past-Due PENDING Invoices as OVERDUE and Assess Late Fee ---
        List<Payment> overdueCandidates = paymentRepository.findByStatusAndDueDateBefore(PaymentStatus.PENDING, LocalDate.now());
        for (Payment candidate : overdueCandidates) {
            candidate.setStatus(PaymentStatus.OVERDUE);

            // Add late fee if not already assessed
            if (candidate.getLateFee() == null || candidate.getLateFee().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal lateFee = candidate.getAmount()
                        .multiply(dynamicLateFeePct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                candidate.setLateFee(lateFee);
                candidate.setAmount(candidate.getAmount().add(lateFee));
                totalLateFeesAssessed = totalLateFeesAssessed.add(lateFee);
                lateFeesCount++;

                String feeMemo = String.format(" [Late fee of $%s (%.1f%%) assessed on %s]",
                        lateFee.toPlainString(), dynamicLateFeePct.doubleValue(), LocalDate.now());
                candidate.setNotes(candidate.getNotes() != null ? candidate.getNotes() + feeMemo : feeMemo);
            }

            paymentRepository.save(candidate);
            overdueCount++;

            // Create in-app notification for overdue invoice
            try {
                if (candidate.getLease() != null && candidate.getLease().getTenant() != null && candidate.getLease().getTenant().getUser() != null) {
                    notificationService.createNotification(
                            candidate.getLease().getTenant().getUser(),
                            "Rent Overdue Notice",
                            "Your invoice (" + candidate.getTransactionReference() + ") is past due. A late fee of $" + candidate.getLateFee() + " has been applied.",
                            com.propio.model.NotificationType.RENT_OVERDUE,
                            "/payments"
                    );
                }
            } catch (Exception ex) {
                log.warn("Failed creating overdue notification for payment #{}: {}", candidate.getId(), ex.getMessage());
            }
        }

        String summary = String.format("[Rent Engine] [%s] Completed run: Generated %d next-month PENDING invoice(s), marked %d overdue invoice(s) (with %d late fee(s) assessed at %.1f%%), %d duplicate(s) skipped.",
                triggerSource, generatedCount, overdueCount, lateFeesCount, dynamicLateFeePct.doubleValue(), skippedDuplicatesCount);
        log.info(summary);

        return BillingCycleResult.builder()
                .generatedInvoicesCount(generatedCount)
                .markedOverdueCount(overdueCount)
                .lateFeesAppliedCount(lateFeesCount)
                .skippedDuplicatesCount(skippedDuplicatesCount)
                .totalLateFeesAssessed(totalLateFeesAssessed)
                .summaryMessage(summary)
                .triggerSource(triggerSource)
                .build();
    }

    /**
     * Generates collision-resistant unique transaction reference matching pattern TXN-yyyyMMdd-xxxxx
     */
    public String generateUniqueTransactionReference(LocalDate date) {
        String dateSegment = (date != null ? date : LocalDate.now()).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String candidateRef;
        do {
            candidateRef = "TXN-" + dateSegment + "-" + generateRandomAlphanumeric(5);
        } while (paymentRepository.existsByTransactionReference(candidateRef));
        return candidateRef;
    }

    private String generateRandomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}

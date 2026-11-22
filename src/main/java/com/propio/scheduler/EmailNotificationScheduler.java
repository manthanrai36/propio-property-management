package com.propio.scheduler;

import com.propio.model.EmailType;
import com.propio.model.Lease;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.repository.EmailLogRepository;
import com.propio.repository.LeaseRepository;
import com.propio.repository.PaymentRepository;
import com.propio.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled daemon responsible for automated resident notifications:
 * 1. Rent due soon reminders (dispatched 3 days prior to due date).
 * 2. Overdue rent alerts for delinquent invoices.
 * 3. Lease renewal / expiration reminders (60 days prior to contract termination).
 *
 * Implements strict duplicate dispatch protection via EmailLogRepository check.
 */
@Component
@Slf4j
public class EmailNotificationScheduler {

    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;
    private final PaymentRepository paymentRepository;
    private final LeaseRepository leaseRepository;

    @Value("${propio.email.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public EmailNotificationScheduler(
            EmailService emailService,
            EmailLogRepository emailLogRepository,
            PaymentRepository paymentRepository,
            LeaseRepository leaseRepository) {
        this.emailService = emailService;
        this.emailLogRepository = emailLogRepository;
        this.paymentRepository = paymentRepository;
        this.leaseRepository = leaseRepository;
    }

    /**
     * Executes once on application readiness for immediate demo validation.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!schedulerEnabled) {
            log.info("EmailNotificationScheduler startup run disabled via configuration.");
            return;
        }
        log.info("Running initial EmailNotificationScheduler job on application startup...");
        processAllNotifications();
    }

    /**
     * Recurring daily schedule (default: 8:00 AM every morning).
     */
    @Scheduled(cron = "${propio.email.scheduler.cron:0 0 8 * * ?}")
    public void scheduledRun() {
        if (!schedulerEnabled) {
            return;
        }
        log.info("Starting scheduled daily EmailNotificationScheduler run...");
        processAllNotifications();
    }

    @Transactional(readOnly = true)
    public void processAllNotifications() {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysOut = today.plusDays(3);
        LocalDate sixtyDaysOut = today.plusDays(60);

        int dueSoonCount = 0;
        int overdueCount = 0;
        int leaseExpiringCount = 0;

        // 1. Rent Due Soon (Invoices due within next 3 days that are still PENDING)
        List<Payment> upcomingPayments = paymentRepository.findUpcomingDuePaymentsWithDetails();
        for (Payment payment : upcomingPayments) {
            if (payment.getStatus() == PaymentStatus.PENDING &&
                payment.getDueDate() != null &&
                !payment.getDueDate().isBefore(today) &&
                !payment.getDueDate().isAfter(threeDaysOut)) {

                if (!emailLogRepository.hasSentEmail(EmailType.RENT_DUE_SOON, payment.getId())) {
                    emailService.sendRentDueSoonEmail(payment);
                    dueSoonCount++;
                }
            }
        }

        // 2. Rent Overdue (Invoices marked OVERDUE)
        List<Payment> overduePayments = paymentRepository.findWithDetailsByStatus(PaymentStatus.OVERDUE);
        for (Payment payment : overduePayments) {
            if (!emailLogRepository.hasSentEmail(EmailType.RENT_OVERDUE, payment.getId())) {
                emailService.sendRentOverdueEmail(payment);
                overdueCount++;
            }
        }

        // 3. Leases Expiring (Active leases ending within the next 60 days)
        List<Lease> expiringLeases = leaseRepository.findUpcomingExpiringLeasesWithDetails();
        for (Lease lease : expiringLeases) {
            if (lease.getEndDate() != null &&
                !lease.getEndDate().isBefore(today) &&
                !lease.getEndDate().isAfter(sixtyDaysOut)) {

                if (!emailLogRepository.hasSentEmail(EmailType.LEASE_EXPIRING, lease.getId())) {
                    long daysRemaining = ChronoUnit.DAYS.between(today, lease.getEndDate());
                    emailService.sendLeaseExpiringEmail(lease, daysRemaining);
                    leaseExpiringCount++;
                }
            }
        }

        log.info("EmailNotificationScheduler completed: Dispatched {} rent due soon, {} overdue notices, {} lease expiring notices (duplicates prevented).",
                dueSoonCount, overdueCount, leaseExpiringCount);
    }
}

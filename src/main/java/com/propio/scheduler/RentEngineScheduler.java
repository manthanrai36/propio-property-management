package com.propio.scheduler;

import com.propio.dto.BillingCycleResult;
import com.propio.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job coordinating automated monthly rent invoice generation and overdue late fees.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RentEngineScheduler {

    private final PaymentService paymentService;

    @Value("${propio.billing.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Executes once on application boot for demo data initialization and consistency verification.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[Rent Engine] Application ready. Triggering initial billing cycle check for demo data...");
        try {
            BillingCycleResult result = paymentService.runBillingCycle("Startup Initializer");
            log.info("[Rent Engine] Startup execution outcome: {}", result.getSummaryMessage());
        } catch (Exception e) {
            log.error("[Rent Engine] Error during startup billing cycle execution: {}", e.getMessage(), e);
        }
    }

    /**
     * Configurable automated billing cycle execution (e.g. 1st of every month at 01:00 AM).
     */
    @Scheduled(cron = "${propio.billing.scheduler.cron:0 0 1 1 * ?}")
    public void scheduledBillingRun() {
        if (!schedulerEnabled) {
            log.debug("[Rent Engine] Scheduled billing cycle is disabled by configuration.");
            return;
        }

        log.info("[Rent Engine] Scheduled cron billing cycle triggered.");
        try {
            paymentService.runBillingCycle("Scheduled Cron Job");
        } catch (Exception e) {
            log.error("[Rent Engine] Scheduled billing cycle execution failed: {}", e.getMessage(), e);
        }
    }
}

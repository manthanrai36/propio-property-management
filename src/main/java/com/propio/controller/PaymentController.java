package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.*;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controller managing rental payments, invoices, record payment modal,
 * automated rent engine execution, tenant payments portal, and checkout flow.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    public String payments(
            @ModelAttribute("filter") PaymentFilterDto filter,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "payments");
        model.addAttribute("user", userDetails);

        // Role-based routing
        if (userDetails != null && userDetails.isTenant()) {
            return showTenantPayments(userDetails, model);
        }

        // Admin: Payments Ledger & Ledger Management
        Page<Payment> paymentPage = paymentService.getAdminPaymentsPaged(filter);
        PaymentTotalsDto totals = paymentService.getPaymentTotals();

        model.addAttribute("paymentPage", paymentPage);
        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("totals", totals);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", PaymentStatus.values());

        if (!model.containsAttribute("recordPaymentForm")) {
            model.addAttribute("recordPaymentForm", new RecordPaymentDto());
        }

        return "payments/admin_list";
    }

    @GetMapping("/tenant/payments")
    @PreAuthorize("hasRole('TENANT')")
    public String tenantPaymentsExplicit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        model.addAttribute("activeNav", "payments");
        model.addAttribute("user", userDetails);
        return showTenantPayments(userDetails, model);
    }

    private String showTenantPayments(CustomUserDetails userDetails, Model model) {
        Long tenantId = userDetails.getTenantId();
        List<Payment> tenantPayments = (tenantId != null)
                ? paymentService.getPaymentsByTenantId(tenantId)
                : Collections.emptyList();

        BigDecimal totalPaid = tenantPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceDue = tenantPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.OVERDUE || p.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Payment> unpaidInvoices = tenantPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.OVERDUE || p.getStatus() == PaymentStatus.PENDING)
                .sorted(Comparator.comparing(Payment::getDueDate))
                .collect(Collectors.toList());

        Payment nextPayment = unpaidInvoices.isEmpty() ? null : unpaidInvoices.get(0);
        boolean hasOverdue = tenantPayments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.OVERDUE);

        model.addAttribute("payments", tenantPayments);
        model.addAttribute("unpaidInvoices", unpaidInvoices);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("balanceDue", balanceDue);
        model.addAttribute("nextPayment", nextPayment);
        model.addAttribute("hasOverdue", hasOverdue);

        return "payments/tenant_list";
    }

    @PostMapping("/payments/record")
    @PreAuthorize("hasRole('ADMIN')")
    public String recordPayment(
            @Valid @ModelAttribute("recordPaymentForm") RecordPaymentDto formDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid payment record data. Please ensure payment method and valid paid date are specified.");
            return "redirect:/payments";
        }

        Payment payment = paymentService.recordPayment(formDto);
        redirectAttributes.addFlashAttribute("successMessage",
                String.format("Payment of $%s for %s (%s) successfully recorded as PAID via %s.",
                        payment.getAmount().toPlainString(),
                        payment.getLease().getTenant().getFullName(),
                        payment.getTransactionReference(),
                        payment.getPaymentMethod()));

        return "redirect:/payments";
    }

    @PostMapping("/payments/run-engine")
    @PreAuthorize("hasRole('ADMIN')")
    public Object triggerRentEngine(
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        log.info("Admin triggered manual Rent Engine billing cycle");
        BillingCycleResult result = paymentService.runBillingCycle("Admin Manual Trigger");

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok(result);
        }

        redirectAttributes.addFlashAttribute("successMessage", result.getSummaryMessage());
        return "redirect:/payments";
    }

    @GetMapping("/payments/{id}/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String showCheckoutPage(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        Payment payment = paymentService.getPaymentById(id);
        verifyPaymentAccess(payment, userDetails);

        if (payment.getStatus() == PaymentStatus.PAID) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Invoice '" + payment.getTransactionReference() + "' is already settled. Viewing receipt.");
            return "redirect:/payments/" + id + "/receipt";
        }

        model.addAttribute("activeNav", "payments");
        model.addAttribute("user", userDetails);
        model.addAttribute("payment", payment);

        if (!model.containsAttribute("checkoutForm")) {
            String tenantName = payment.getLease().getTenant().getFullName();
            model.addAttribute("checkoutForm", SimulatedCheckoutDto.builder()
                    .cardholderName(tenantName)
                    .cardNumberMasked("4532 8920 1024 8899")
                    .expiry("12/28")
                    .cvv("742")
                    .simulationOutcome("SUCCESS")
                    .build());
        }

        return "payments/checkout";
    }

    @PostMapping("/payments/{id}/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String processCheckout(
            @PathVariable Long id,
            @Valid @ModelAttribute("checkoutForm") SimulatedCheckoutDto formDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Payment payment = paymentService.getPaymentById(id);
        verifyPaymentAccess(payment, userDetails);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide a valid cardholder name.");
            return "redirect:/payments/" + id + "/checkout";
        }

        paymentService.processOnlinePayment(id, formDto);
        redirectAttributes.addFlashAttribute("successMessage",
                "Payment successfully authorized and settled! Thank you for your payment.");
        return "redirect:/payments/" + id + "/receipt";
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String showReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Payment payment = paymentService.getPaymentById(id);
        verifyPaymentAccess(payment, userDetails);

        model.addAttribute("activeNav", "payments");
        model.addAttribute("user", userDetails);
        model.addAttribute("payment", payment);

        return "payments/receipt";
    }

    private void verifyPaymentAccess(Payment payment, CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.isTenant()) {
            Long tenantId = userDetails.getTenantId();
            if (tenantId == null || payment.getLease() == null || payment.getLease().getTenant() == null
                    || !payment.getLease().getTenant().getId().equals(tenantId)) {
                throw new AccessDeniedException("You are only authorized to access payment transactions for your own resident account.");
            }
        }
    }
}

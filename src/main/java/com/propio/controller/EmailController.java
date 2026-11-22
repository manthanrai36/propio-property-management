package com.propio.controller;

import com.propio.dto.EmailFilterDto;
import com.propio.dto.TestEmailDto;
import com.propio.model.EmailLog;
import com.propio.model.EmailStatus;
import com.propio.model.EmailType;
import com.propio.repository.EmailLogRepository;
import com.propio.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for Administrative Email Management and Audit Log Inspection:
 * - View filtered, paginated email dispatch logs with status badges and search.
 * - Inspect rendered HTML email bodies via preview modal.
 * - Trigger immediate re-dispatch of any logged email.
 * - Send manual diagnostic test emails.
 *
 * Restricted strictly to ADMIN role.
 */
@Controller
@RequestMapping("/emails")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;

    @GetMapping
    public String listEmailLogs(
            @ModelAttribute("filter") EmailFilterDto filter,
            Model model) {

        int pageNum = Math.max(0, filter.getPage());
        int pageSize = filter.getSize() > 0 ? filter.getSize() : 15;
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        Page<EmailLog> logs = emailLogRepository.searchLogs(
                filter.getType(),
                filter.getStatus(),
                filter.getSearch(),
                pageable);

        // System Diagnostic & Volume Metrics
        long totalDispatched = emailLogRepository.countByStatusIn(List.of(EmailStatus.SENT, EmailStatus.SIMULATED_SENT));
        long pendingRetrying = emailLogRepository.countByStatus(EmailStatus.RETRYING);
        long failedCount = emailLogRepository.countByStatus(EmailStatus.FAILED);
        long recent24h = emailLogRepository.countByCreatedAtAfter(LocalDateTime.now().minusHours(24));

        model.addAttribute("logs", logs);
        model.addAttribute("filter", filter);
        model.addAttribute("emailTypes", EmailType.values());
        model.addAttribute("emailStatuses", EmailStatus.values());
        model.addAttribute("totalDispatched", totalDispatched);
        model.addAttribute("pendingRetrying", pendingRetrying);
        model.addAttribute("failedCount", failedCount);
        model.addAttribute("recent24h", recent24h);
        model.addAttribute("mailEnabled", emailService.isMailEnabled());
        model.addAttribute("fromAddress", emailService.getFromAddress());

        if (!model.containsAttribute("testEmailDto")) {
            model.addAttribute("testEmailDto", TestEmailDto.builder()
                    .subject("Propio Diagnostic Test")
                    .message("Testing notification engine integration.")
                    .build());
        }

        return "emails/admin_list";
    }

    @PostMapping("/test")
    public String sendTestEmail(
            @Valid @ModelAttribute("testEmailDto") TestEmailDto dto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Validation failed: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/emails";
        }

        try {
            emailService.sendTestEmail(dto.getRecipient().trim(), dto.getMessage());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Test email queued for asynchronous dispatch to " + dto.getRecipient() + ".");
        } catch (Exception ex) {
            log.error("Failed to queue test email to {}: {}", dto.getRecipient(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error initiating test dispatch: " + ex.getMessage());
        }

        return "redirect:/emails";
    }

    @PostMapping("/{id}/resend")
    public String resendEmail(
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes) {

        try {
            emailService.resendEmail(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Email #" + id + " has been queued for immediate re-dispatch.");
        } catch (Exception ex) {
            log.error("Failed to resend email #{}: {}", id, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error resending email: " + ex.getMessage());
        }

        return "redirect:/emails";
    }

    @GetMapping(value = "/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> previewEmailHtml(@PathVariable("id") Long id) {
        return emailLogRepository.findById(id)
                .map(log -> ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(log.getHtmlBody() != null ? log.getHtmlBody() : "<p>No HTML body recorded.</p>"))
                .orElse(ResponseEntity.notFound().build());
    }
}

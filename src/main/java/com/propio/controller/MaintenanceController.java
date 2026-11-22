package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.*;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.*;
import com.propio.repository.PropertyRepository;
import com.propio.repository.UserRepository;
import com.propio.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller managing the complete maintenance request lifecycle for both Admin and Tenant roles.
 */
@Controller
@RequestMapping("/maintenance")
@RequiredArgsConstructor
@Slf4j
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    private static final List<String> CATEGORIES = Arrays.asList(
            "Plumbing", "Electrical", "HVAC", "Appliance", "Structural", "Pest Control", "General"
    );

    private static final List<String> PRIORITIES = Arrays.asList(
            "LOW", "MEDIUM", "HIGH", "EMERGENCY"
    );

    private static final List<String> DEFAULT_TECHNICIANS = Arrays.asList(
            "Mike Peterson (Plumbing)",
            "Elena Rostova (Electrical)",
            "Marcus Vance (HVAC)",
            "Carlos Sanchez (Appliance & General)",
            "David Miller (Structural Contractor)"
    );

    // =========================================================================
    // Admin Console: Board & List View
    // =========================================================================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String adminMaintenanceConsole(
            @ModelAttribute("filter") MaintenanceFilterDto filter,
            Model model) {

        model.addAttribute("activeNav", "maintenance");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("priorities", PRIORITIES);
        model.addAttribute("technicians", DEFAULT_TECHNICIANS);
        model.addAttribute("properties", propertyRepository.findAll());
        model.addAttribute("statuses", RequestStatus.values());

        // Overall counts for summary cards
        model.addAttribute("openCount", maintenanceService.getOpenCount());
        model.addAttribute("inProgressCount", maintenanceService.getInProgressCount());
        model.addAttribute("resolvedCount", maintenanceService.getResolvedCount());
        model.addAttribute("closedCount", maintenanceService.getClosedCount());
        model.addAttribute("totalCost", maintenanceService.getTotalEstimatedCost());

        if ("list".equalsIgnoreCase(filter.getViewMode())) {
            Page<MaintenanceRequest> pagedRequests = maintenanceService.getAdminRequestsPaged(filter);
            model.addAttribute("pagedRequests", pagedRequests);
        } else {
            // Default: Kanban Board View
            List<MaintenanceRequest> allFiltered = maintenanceService.getAllAdminRequestsFiltered(filter);

            List<MaintenanceRequest> openList = allFiltered.stream()
                    .filter(r -> r.getStatus().isOpen())
                    .collect(Collectors.toList());

            List<MaintenanceRequest> inProgressList = allFiltered.stream()
                    .filter(r -> r.getStatus() == RequestStatus.IN_PROGRESS)
                    .collect(Collectors.toList());

            List<MaintenanceRequest> resolvedList = allFiltered.stream()
                    .filter(r -> r.getStatus() == RequestStatus.RESOLVED)
                    .collect(Collectors.toList());

            List<MaintenanceRequest> closedList = allFiltered.stream()
                    .filter(r -> r.getStatus() == RequestStatus.CLOSED || r.getStatus() == RequestStatus.CANCELLED)
                    .collect(Collectors.toList());

            model.addAttribute("openList", openList);
            model.addAttribute("inProgressList", inProgressList);
            model.addAttribute("resolvedList", resolvedList);
            model.addAttribute("closedList", closedList);
        }

        if (!model.containsAttribute("updateStatusDto")) {
            model.addAttribute("updateStatusDto", new UpdateMaintenanceStatusDto());
        }
        if (!model.containsAttribute("assignDto")) {
            model.addAttribute("assignDto", new AssignTechnicianDto());
        }

        return "maintenance/admin_board";
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStatus(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("updateStatusDto") UpdateMaintenanceStatusDto dto,
            BindingResult bindingResult,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Validation failed: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return redirectUrl != null ? "redirect:" + redirectUrl : "redirect:/maintenance";
        }

        try {
            User user = userDetails != null ? userRepository.findById(userDetails.getId()).orElse(null) : null;
            MaintenanceRequest updated = maintenanceService.updateStatus(id, dto, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Request #" + id + " status successfully updated to " + updated.getStatus().getDisplayName() + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update status for maintenance request #{}: {}", id, ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating request status: " + ex.getMessage());
        }

        return redirectUrl != null ? "redirect:" + redirectUrl : "redirect:/maintenance";
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public String assignTechnician(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("assignDto") AssignTechnicianDto dto,
            BindingResult bindingResult,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Validation failed: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return redirectUrl != null ? "redirect:" + redirectUrl : "redirect:/maintenance";
        }

        try {
            User user = userDetails != null ? userRepository.findById(userDetails.getId()).orElse(null) : null;
            maintenanceService.assignTechnician(id, dto, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Technician '" + dto.getTechnicianName() + "' assigned to request #" + id + ".");
        } catch (Exception ex) {
            log.error("Failed to assign technician for request #{}: {}", id, ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning technician: " + ex.getMessage());
        }

        return redirectUrl != null ? "redirect:" + redirectUrl : "redirect:/maintenance";
    }

    // =========================================================================
    // Tenant Console: Submission & My Requests
    // =========================================================================

    @GetMapping("/new")
    @PreAuthorize("hasRole('TENANT')")
    public String tenantNewRequestForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "maintenance");
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("priorities", PRIORITIES);

        if (!model.containsAttribute("requestDto")) {
            model.addAttribute("requestDto", new CreateMaintenanceRequestDto());
        }
        return "maintenance/tenant_new";
    }

    @PostMapping("/new")
    @PreAuthorize("hasRole('TENANT')")
    public String submitTenantRequest(
            @Valid @ModelAttribute("requestDto") CreateMaintenanceRequestDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "maintenance");
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("priorities", PRIORITIES);
            return "maintenance/tenant_new";
        }

        try {
            User user = userDetails != null ? userRepository.findById(userDetails.getId()).orElse(null) : null;
            MaintenanceRequest created = maintenanceService.createTenantRequest(dto, userDetails.getTenantId(), user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Service request #" + created.getId() + " submitted successfully. Our maintenance team has been notified.");
            return "redirect:/maintenance/my-requests";
        } catch (Exception ex) {
            log.error("Error submitting maintenance request: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activeNav", "maintenance");
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("priorities", PRIORITIES);
            return "maintenance/tenant_new";
        }
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('TENANT')")
    public String tenantRequestsList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "maintenance");
        List<MaintenanceRequest> myRequests = maintenanceService.getTenantRequests(userDetails.getTenantId());
        model.addAttribute("myRequests", myRequests);

        long openCount = myRequests.stream().filter(r -> r.getStatus().isOpen()).count();
        long inProgressCount = myRequests.stream().filter(r -> r.getStatus() == RequestStatus.IN_PROGRESS).count();
        long resolvedCount = myRequests.stream().filter(r -> r.getStatus().isClosedOrResolved()).count();

        model.addAttribute("openCount", openCount);
        model.addAttribute("inProgressCount", inProgressCount);
        model.addAttribute("resolvedCount", resolvedCount);

        return "maintenance/tenant_list";
    }

    // =========================================================================
    // Request Details & Comment Thread (Shared)
    // =========================================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String requestDetails(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "maintenance");

        MaintenanceRequest request;
        if (userDetails.isAdmin()) {
            request = maintenanceService.getRequestById(id);
        } else {
            request = maintenanceService.getRequestForTenant(id, userDetails.getTenantId());
        }

        List<MaintenanceComment> comments = maintenanceService.getRequestComments(id);

        model.addAttribute("request", request);
        model.addAttribute("comments", comments);
        model.addAttribute("isAdmin", userDetails.isAdmin());
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("technicians", DEFAULT_TECHNICIANS);
        model.addAttribute("updateStatusDto", UpdateMaintenanceStatusDto.builder()
                .status(request.getStatus())
                .assignedTechnician(request.getAssignedTechnician())
                .scheduledDate(request.getScheduledDate())
                .estimatedCost(request.getEstimatedCost())
                .actualCost(request.getActualCost())
                .build());

        if (!model.containsAttribute("assignDto")) {
            model.addAttribute("assignDto", AssignTechnicianDto.builder()
                    .technicianName(request.getAssignedTechnician())
                    .scheduledDate(request.getScheduledDate())
                    .build());
        }

        return "maintenance/details";
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String addComment(
            @PathVariable("id") Long id,
            @RequestParam("content") String content,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (content == null || content.trim().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Comment cannot be blank.");
            return "redirect:/maintenance/" + id;
        }

        try {
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));

            maintenanceService.addComment(id, content, user);
            redirectAttributes.addFlashAttribute("successMessage", "Comment posted to discussion thread.");
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to post comment to request #{}: {}", id, ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error posting comment: " + ex.getMessage());
        }

        return "redirect:/maintenance/" + id;
    }
}

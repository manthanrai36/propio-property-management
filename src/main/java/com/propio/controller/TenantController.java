package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.TenantCreationResult;
import com.propio.dto.TenantFormDto;
import com.propio.dto.TenantSummaryDto;
import com.propio.model.*;
import com.propio.repository.DocumentRepository;
import com.propio.repository.LeaseRepository;
import com.propio.repository.MaintenanceRequestRepository;
import com.propio.repository.PaymentRepository;
import com.propio.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller managing tenant administration, search/filter portfolio,
 * creation with automated user credentials, and detailed profile views.
 */
@Controller
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

    private final TenantService tenantService;
    private final LeaseRepository leaseRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final DocumentRepository documentRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String listTenants(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending());
        Page<TenantSummaryDto> tenantsPage = tenantService.getTenantsPaged(search, filter, pageable);

        model.addAttribute("activeNav", "tenants");
        model.addAttribute("user", userDetails);
        model.addAttribute("tenantsPage", tenantsPage);
        model.addAttribute("search", search);
        model.addAttribute("filter", filter);

        return "tenants/list";
    }

    @GetMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public String showAddTenantForm(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "tenants");
        model.addAttribute("user", userDetails);
        if (!model.containsAttribute("tenantForm")) {
            model.addAttribute("tenantForm", new TenantFormDto());
        }
        model.addAttribute("isEdit", false);
        return "tenants/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") TenantFormDto formDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "tenants");
            model.addAttribute("user", userDetails);
            model.addAttribute("isEdit", false);
            return "tenants/form";
        }

        try {
            TenantCreationResult result = tenantService.createTenant(formDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tenant profile for " + result.getTenant().getFullName() + " created successfully!");
            redirectAttributes.addFlashAttribute("tempPassword", result.getRawTemporaryPassword());
            redirectAttributes.addFlashAttribute("createdTenantEmail", result.getLoginEmail());
            redirectAttributes.addFlashAttribute("showTempPasswordModal", true);
            return "redirect:/tenants";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("email", "duplicate.email", e.getMessage());
            model.addAttribute("activeNav", "tenants");
            model.addAttribute("user", userDetails);
            model.addAttribute("isEdit", false);
            return "tenants/form";
        }
    }

    @GetMapping("/{id}")
    public String tenantDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        // Access check: ADMIN can view anyone; TENANT can view only their own profile
        if (userDetails != null && userDetails.isTenant()) {
            if (userDetails.getTenantId() == null || !userDetails.getTenantId().equals(id)) {
                throw new AccessDeniedException("You are only permitted to view your own tenant profile.");
            }
        }

        Tenant tenant = tenantService.getTenantById(id);
        TenantSummaryDto summary = tenantService.mapToSummaryDto(tenant);

        Lease activeLease = leaseRepository.findActiveLeaseByTenantIdWithDetails(id).orElse(null);
        List<Lease> allLeases = leaseRepository.findByTenantIdWithDetails(id);
        List<Payment> payments = paymentRepository.findByTenantIdWithDetails(id);
        List<MaintenanceRequest> requests = maintenanceRequestRepository.findByTenantId(id);
        List<Document> documents = documentRepository.findByTenantId(id);

        model.addAttribute("activeNav", "tenants");
        model.addAttribute("user", userDetails);
        model.addAttribute("tenant", tenant);
        model.addAttribute("summary", summary);
        model.addAttribute("activeLease", activeLease);
        model.addAttribute("allLeases", allLeases);
        model.addAttribute("payments", payments);
        model.addAttribute("requests", requests);
        model.addAttribute("documents", documents);

        return "tenants/details";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String showEditTenantForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Tenant tenant = tenantService.getTenantById(id);
        TenantFormDto formDto = TenantFormDto.builder()
                .id(tenant.getId())
                .firstName(tenant.getFirstName())
                .lastName(tenant.getLastName())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .occupation(tenant.getOccupation())
                .emergencyContact(tenant.getEmergencyContact())
                .avatarUrl(tenant.getAvatarUrl())
                .build();

        model.addAttribute("activeNav", "tenants");
        model.addAttribute("user", userDetails);
        model.addAttribute("tenantForm", formDto);
        model.addAttribute("isEdit", true);
        return "tenants/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateTenant(
            @PathVariable Long id,
            @Valid @ModelAttribute("tenantForm") TenantFormDto formDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "tenants");
            model.addAttribute("user", userDetails);
            model.addAttribute("isEdit", true);
            return "tenants/form";
        }

        tenantService.updateTenant(id, formDto);
        redirectAttributes.addFlashAttribute("successMessage", "Tenant profile updated successfully!");
        return "redirect:/tenants/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteTenant(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        tenantService.deleteTenant(id);
        redirectAttributes.addFlashAttribute("successMessage", "Tenant profile removed successfully.");
        return "redirect:/tenants";
    }
}

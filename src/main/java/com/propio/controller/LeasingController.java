package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.LeaseFormDto;
import com.propio.model.*;
import com.propio.repository.DocumentRepository;
import com.propio.repository.PaymentRepository;
import com.propio.repository.TenantRepository;
import com.propio.repository.UnitRepository;
import com.propio.service.LeaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller handling lease agreements:
 * - ADMIN: Full portfolio leasing lifecycle, creation with overlap validation, termination, payment schedule.
 * - TENANT: Dedicated "My Leases" portal viewing their active contracts and billing schedules.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LeasingController {

    private final LeaseService leaseService;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentRepository documentRepository;

    @GetMapping("/leasing")
    public String leasing(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "leasing");
        model.addAttribute("user", userDetails);

        // Role-based routing: Tenant sees their own leases portal
        if (userDetails != null && userDetails.isTenant()) {
            return showTenantLeases(userDetails, model);
        }

        // Admin: Show full leasing management dashboard
        Pageable pageable = PageRequest.of(page, size, Sort.by("active").descending().and(Sort.by("endDate").ascending()));
        Page<Lease> leasesPage = leaseService.getLeasesPaged(search, filter, pageable);

        model.addAttribute("leasesPage", leasesPage);
        model.addAttribute("search", search);
        model.addAttribute("filter", filter);
        model.addAttribute("activeCount", leaseService.countActiveLeases());
        model.addAttribute("expiringCount", leaseService.countExpiringSoonLeases());
        model.addAttribute("monthlyRentSum", leaseService.getActiveMonthlyRentSum());

        // Supply data for the Create Lease modal
        if (!model.containsAttribute("leaseForm")) {
            model.addAttribute("leaseForm", new LeaseFormDto());
        }
        model.addAttribute("allTenants", tenantRepository.findAll());
        model.addAttribute("vacantUnits", unitRepository.findByStatusWithProperty(UnitStatus.VACANT));

        return "leasing/list";
    }

    @GetMapping("/tenant/leases")
    @PreAuthorize("hasRole('TENANT')")
    public String tenantLeasesExplicit(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "leasing");
        model.addAttribute("user", userDetails);
        return showTenantLeases(userDetails, model);
    }

    private String showTenantLeases(CustomUserDetails userDetails, Model model) {
        Long tenantId = userDetails.getTenantId();
        List<Lease> myLeases = (tenantId != null) ? leaseService.getLeasesByTenantId(tenantId) : List.of();

        Lease primaryLease = myLeases.stream().filter(Lease::isActive).findFirst().orElse(
                myLeases.isEmpty() ? null : myLeases.get(0)
        );

        List<Payment> payments = (primaryLease != null)
                ? paymentRepository.findByLeaseIdOrderByDueDateAsc(primaryLease.getId())
                : List.of();

        List<Document> documents = (primaryLease != null)
                ? documentRepository.findByLeaseId(primaryLease.getId())
                : List.of();

        model.addAttribute("myLeases", myLeases);
        model.addAttribute("primaryLease", primaryLease);
        model.addAttribute("payments", payments);
        model.addAttribute("documents", documents);

        return "leasing/tenant_leases";
    }

    @GetMapping("/leasing/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String showCreateLeaseForm(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "leasing");
        model.addAttribute("user", userDetails);
        if (!model.containsAttribute("leaseForm")) {
            model.addAttribute("leaseForm", new LeaseFormDto());
        }
        model.addAttribute("allTenants", tenantRepository.findAll());
        model.addAttribute("vacantUnits", unitRepository.findByStatusWithProperty(UnitStatus.VACANT));

        return "leasing/form";
    }

    @PostMapping("/leasing")
    @PreAuthorize("hasRole('ADMIN')")
    public String createLease(
            @Valid @ModelAttribute("leaseForm") LeaseFormDto formDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "leasing");
            model.addAttribute("user", userDetails);
            model.addAttribute("allTenants", tenantRepository.findAll());
            model.addAttribute("vacantUnits", unitRepository.findByStatusWithProperty(UnitStatus.VACANT));
            return "leasing/form";
        }

        try {
            Lease lease = leaseService.createLease(formDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Lease agreement successfully created for Unit '" +
                            lease.getUnit().getUnitNumber() + "'. Monthly payment schedule automatically generated!");
            return "redirect:/leasing";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("endDate", "invalid.endDate", ex.getMessage());
            model.addAttribute("activeNav", "leasing");
            model.addAttribute("user", userDetails);
            model.addAttribute("allTenants", tenantRepository.findAll());
            model.addAttribute("vacantUnits", unitRepository.findByStatusWithProperty(UnitStatus.VACANT));
            return "leasing/form";
        }
    }

    @PostMapping("/leasing/{id}/terminate")
    @PreAuthorize("hasRole('ADMIN')")
    public String terminateLease(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        leaseService.terminateLease(id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Lease agreement terminated. Unit restored to Vacant status.");
        return "redirect:/leasing";
    }
}

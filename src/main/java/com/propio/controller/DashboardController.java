package com.propio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propio.config.CustomUserDetails;
import com.propio.dto.TenantDashboardDto;
import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller serving role-personalized dashboards:
 * - ADMIN: Views aggregate property portfolio, financial metrics, and operational health.
 * - TENANT: Views their own leased unit, active agreements, rent balances, and service requests.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final DocumentRepository documentRepository;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("user", userDetails);

        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        model.addAttribute("isAdmin", isAdmin);

        if (isAdmin) {
            populateAdminDashboard(model);
        } else {
            populateTenantDashboard(userDetails, model);
        }

        return "dashboard";
    }

    private void populateAdminDashboard(Model model) {
        // 1. Single query grouping units by status so donut, center total and legend always match
        List<Object[]> statusRows = unitRepository.countGroupedByStatus();
        Map<UnitStatus, Long> countsMap = new EnumMap<>(UnitStatus.class);
        for (Object[] row : statusRows) {
            countsMap.put((UnitStatus) row[0], (Long) row[1]);
        }
        long occupiedUnits = countsMap.getOrDefault(UnitStatus.OCCUPIED, 0L);
        long bookedUnits = countsMap.getOrDefault(UnitStatus.BOOKED, 0L);
        long vacantUnits = countsMap.getOrDefault(UnitStatus.VACANT, 0L);
        long maintenanceUnits = countsMap.getOrDefault(UnitStatus.MAINTENANCE, 0L);
        long totalUnits = occupiedUnits + bookedUnits + vacantUnits + maintenanceUnits;

        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("occupiedUnits", occupiedUnits);
        model.addAttribute("bookedUnits", bookedUnits);
        model.addAttribute("vacantUnits", vacantUnits);
        model.addAttribute("maintenanceUnits", maintenanceUnits);

        // 2. Real Financial totals from database
        BigDecimal totalOverdue = paymentRepository.sumAmountByStatus(PaymentStatus.OVERDUE);
        BigDecimal totalPaid = paymentRepository.sumAmountByStatus(PaymentStatus.PAID);
        BigDecimal totalPending = paymentRepository.sumAmountByStatus(PaymentStatus.PENDING);
        long overdueCount = paymentRepository.countByStatus(PaymentStatus.OVERDUE);
        long upcomingCount = paymentRepository.countByStatus(PaymentStatus.PENDING);
        BigDecimal totalMaintenanceCost = maintenanceRequestRepository.sumTotalEstimatedCost();

        model.addAttribute("totalOverdue", totalOverdue != null ? totalOverdue : BigDecimal.ZERO);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("totalUpcoming", totalPending != null ? totalPending : BigDecimal.ZERO);
        model.addAttribute("upcomingCount", upcomingCount);
        model.addAttribute("totalPaidRevenue", totalPaid != null ? totalPaid : BigDecimal.ZERO);
        model.addAttribute("totalMaintenanceCost", totalMaintenanceCost != null ? totalMaintenanceCost : BigDecimal.ZERO);

        // 3. Real 3 Event Cards with real tenants from database
        List<Lease> expiringLeases = leaseRepository.findUpcomingExpiringLeasesWithDetails();
        Lease expiringLease = expiringLeases.isEmpty() ? null : expiringLeases.get(0);
        model.addAttribute("expiringLease", expiringLease);

        List<Payment> duePayments = paymentRepository.findUpcomingDuePaymentsWithDetails();
        Payment duePayment = duePayments.isEmpty() ? null : duePayments.get(0);
        model.addAttribute("duePayment", duePayment);

        List<MaintenanceRequest> openRequests = maintenanceRequestRepository.findOpenRequestsWithDetails();
        MaintenanceRequest openRequest = openRequests.isEmpty() ? null : openRequests.get(0);
        model.addAttribute("openRequest", openRequest);

        // 4. Real Rental Payments chart using real payment dates from the last 30 days
        List<Payment> paidPayments = paymentRepository.findPaidPaymentsSince(LocalDate.now().minusDays(30));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        List<String> paymentLabels = new ArrayList<>();
        List<BigDecimal> paymentValues = new ArrayList<>();
        for (Payment p : paidPayments) {
            if (p.getPaidDate() != null) {
                paymentLabels.add(p.getPaidDate().format(formatter));
                paymentValues.add(p.getAmount());
            }
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            model.addAttribute("rentalPaymentLabelsJson", mapper.writeValueAsString(paymentLabels));
            model.addAttribute("rentalPaymentValuesJson", mapper.writeValueAsString(paymentValues));
        } catch (Exception e) {
            model.addAttribute("rentalPaymentLabelsJson", "[]");
            model.addAttribute("rentalPaymentValuesJson", "[]");
        }

        // 5. Real Rent Received progress calculation
        BigDecimal monthlyRentExpected = leaseRepository.findByActiveTrue().stream()
                .map(Lease::getMonthlyRent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (monthlyRentExpected.compareTo(BigDecimal.ZERO) == 0) {
            monthlyRentExpected = new BigDecimal("6000.00");
        }
        double rentPercent = monthlyRentExpected.compareTo(BigDecimal.ZERO) > 0
                ? ((totalPaid != null ? totalPaid.doubleValue() : 0.0) / monthlyRentExpected.doubleValue()) * 100 : 0.0;
        model.addAttribute("monthPaidRent", totalPaid != null ? totalPaid : BigDecimal.ZERO);
        model.addAttribute("monthTotalRent", monthlyRentExpected);
        model.addAttribute("rentReceivedPercent", Math.min(100, (int) Math.round(rentPercent)));

        // 6. Properties & featured listings
        List<Property> properties = propertyRepository.findAll();
        model.addAttribute("properties", properties);
        model.addAttribute("totalProperties", properties.size());
    }

    private void populateTenantDashboard(CustomUserDetails userDetails, Model model) {
        Long tenantId = userDetails != null ? userDetails.getTenantId() : null;

        if (tenantId != null) {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            model.addAttribute("tenantProfile", tenant);

            // Fetch tenant's active lease with eager fetch joins (unit, property, tenant)
            Lease activeLease = leaseRepository.findActiveLeaseByTenantIdWithDetails(tenantId).orElse(null);
            model.addAttribute("activeLease", activeLease);

            // Fetch tenant's payments with eager fetch join
            List<Payment> payments = paymentRepository.findByTenantIdWithDetails(tenantId);
            model.addAttribute("tenantPayments", payments);

            // Fetch tenant's maintenance requests
            List<MaintenanceRequest> requests = maintenanceRequestRepository.findByTenantId(tenantId);
            model.addAttribute("tenantRequests", requests);

            // Fetch tenant's documents
            List<Document> documents = documentRepository.findByTenantId(tenantId);
            model.addAttribute("tenantDocuments", documents);

            // Construct safe, flat View DTO to prevent lazy-loading crashes in Thymeleaf
            BigDecimal balanceDue = payments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.OVERDUE || p.getStatus() == PaymentStatus.PENDING)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean hasOverdue = payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.OVERDUE);

            TenantDashboardDto tenantDto = TenantDashboardDto.builder()
                    .hasActiveLease(activeLease != null)
                    .propertyName(activeLease != null && activeLease.getUnit() != null && activeLease.getUnit().getProperty() != null
                            ? activeLease.getUnit().getProperty().getName() : "Propio Residence")
                    .unitNumber(activeLease != null && activeLease.getUnit() != null
                            ? activeLease.getUnit().getUnitNumber() : "Unit")
                    .propertyAddress(activeLease != null && activeLease.getUnit() != null && activeLease.getUnit().getProperty() != null
                            ? activeLease.getUnit().getProperty().getAddress() : "Address on file")
                    .monthlyRent(activeLease != null && activeLease.getMonthlyRent() != null
                            ? activeLease.getMonthlyRent() : BigDecimal.ZERO)
                    .leaseEndDate(activeLease != null ? activeLease.getEndDate() : null)
                    .currentBalanceDue(balanceDue)
                    .hasOverduePayment(hasOverdue)
                    .openTicketsCount(requests.size())
                    .build();

            model.addAttribute("tenantDashboard", tenantDto);
        } else {
            model.addAttribute("tenantPayments", Collections.emptyList());
            model.addAttribute("tenantRequests", Collections.emptyList());
            model.addAttribute("tenantDocuments", Collections.emptyList());
            model.addAttribute("tenantDashboard", TenantDashboardDto.builder()
                    .hasActiveLease(false)
                    .propertyName("Propio Residence")
                    .unitNumber("Unit")
                    .propertyAddress("No active unit assigned")
                    .monthlyRent(BigDecimal.ZERO)
                    .currentBalanceDue(BigDecimal.ZERO)
                    .hasOverduePayment(false)
                    .openTicketsCount(0)
                    .build());
        }
    }


    @GetMapping("/maintenance")
    public String maintenance(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "maintenance");
        model.addAttribute("user", userDetails);
        if (userDetails != null && userDetails.isTenant() && userDetails.getTenantId() != null) {
            model.addAttribute("requests", maintenanceRequestRepository.findByTenantId(userDetails.getTenantId()));
        } else {
            model.addAttribute("requests", maintenanceRequestRepository.findAll());
        }
        return "dashboard";
    }

    @GetMapping("/documents")
    public String documents(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "documents");
        model.addAttribute("user", userDetails);
        if (userDetails != null && userDetails.isTenant() && userDetails.getTenantId() != null) {
            model.addAttribute("documents", documentRepository.findByTenantId(userDetails.getTenantId()));
        } else {
            model.addAttribute("documents", documentRepository.findAll());
        }
        return "dashboard";
    }
}

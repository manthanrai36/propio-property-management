package com.propio.service;

import com.propio.dto.SearchResultDto;
import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;

    @Transactional(readOnly = true)
    public List<SearchResultDto> search(String query, User user) {
        if (query == null || query.trim().length() < 2 || user == null) {
            return Collections.emptyList();
        }

        String term = query.trim().toLowerCase();
        List<SearchResultDto> results = new ArrayList<>();

        if (user.getRole() == Role.ROLE_ADMIN) {
            searchAdmin(term, results);
        } else if (user.getRole() == Role.ROLE_TENANT) {
            searchTenant(term, user, results);
        }

        log.debug("Global search for '{}' (User: {}) returned {} results", query, user.getEmail(), results.size());
        return results;
    }

    private void searchAdmin(String term, List<SearchResultDto> results) {
        // 1. Properties
        List<Property> properties = propertyRepository.findAll();
        for (Property p : properties) {
            boolean match = (p.getName() != null && p.getName().toLowerCase().contains(term)) ||
                            (p.getAddress() != null && p.getAddress().toLowerCase().contains(term)) ||
                            (p.getCity() != null && p.getCity().toLowerCase().contains(term));
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("PROPERTY")
                        .categoryDisplayName("Property")
                        .title(p.getName())
                        .subtitle(p.getAddress() + ", " + p.getCity())
                        .badge(p.getPropertyType() != null ? p.getPropertyType() : "Property")
                        .badgeClass("bg-primary-subtle text-primary")
                        .url("/properties/" + p.getId())
                        .icon("bi-building")
                        .build());
            }
        }

        // 2. Tenants
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant t : tenants) {
            boolean match = (t.getFullName() != null && t.getFullName().toLowerCase().contains(term)) ||
                            (t.getEmail() != null && t.getEmail().toLowerCase().contains(term)) ||
                            (t.getPhone() != null && t.getPhone().toLowerCase().contains(term));
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("TENANT")
                        .categoryDisplayName("Tenant")
                        .title(t.getFullName())
                        .subtitle(t.getEmail() != null ? t.getEmail() : "No email")
                        .badge("Resident")
                        .badgeClass("bg-success-subtle text-success")
                        .url("/tenants/" + t.getId())
                        .icon("bi-person-fill")
                        .build());
            }
        }

        // 3. Leases
        List<Lease> leases = leaseRepository.findAll();
        for (Lease l : leases) {
            String tenantName = l.getTenant() != null ? l.getTenant().getFullName() : "";
            String unitNum = l.getUnit() != null ? l.getUnit().getUnitNumber() : "";
            String propName = (l.getUnit() != null && l.getUnit().getProperty() != null)
                    ? l.getUnit().getProperty().getName() : "";

            boolean match = tenantName.toLowerCase().contains(term) ||
                            unitNum.toLowerCase().contains(term) ||
                            propName.toLowerCase().contains(term);
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("LEASE")
                        .categoryDisplayName("Lease")
                        .title("Lease #" + l.getId() + " - Unit " + unitNum)
                        .subtitle(tenantName + " • " + propName)
                        .badge(l.isActive() ? "ACTIVE" : "ENDED")
                        .badgeClass(l.isActive() ? "bg-primary-subtle text-primary" : "bg-secondary-subtle text-secondary")
                        .url("/leasing")
                        .icon("bi-file-earmark-text-fill")
                        .build());
            }
        }

        // 4. Payments
        List<Payment> payments = paymentRepository.findAll();
        for (Payment p : payments) {
            String ref = p.getTransactionReference() != null ? p.getTransactionReference() : "";
            String tenantName = (p.getLease() != null && p.getLease().getTenant() != null)
                    ? p.getLease().getTenant().getFullName() : "";
            String method = p.getPaymentMethod() != null ? p.getPaymentMethod() : "";

            boolean match = ref.toLowerCase().contains(term) ||
                            tenantName.toLowerCase().contains(term) ||
                            method.toLowerCase().contains(term);
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("PAYMENT")
                        .categoryDisplayName("Payment")
                        .title(ref.isEmpty() ? "Payment #" + p.getId() : ref)
                        .subtitle(tenantName + " • $" + (p.getAmount() != null ? p.getAmount() : "0.00"))
                        .badge(p.getStatus() != null ? p.getStatus().name() : "")
                        .badgeClass(p.getStatus() == PaymentStatus.PAID ? "bg-success-subtle text-success"
                                : (p.getStatus() == PaymentStatus.OVERDUE ? "bg-danger-subtle text-danger" : "bg-warning-subtle text-warning"))
                        .url("/payments")
                        .icon("bi-credit-card-2-front-fill")
                        .build());
            }
        }
    }

    private void searchTenant(String term, User user, List<SearchResultDto> results) {
        Optional<Tenant> tenantOpt = tenantRepository.findByUserId(user.getId());
        if (tenantOpt.isEmpty()) {
            return;
        }
        Tenant tenant = tenantOpt.get();

        // 1. Tenant's own Leases & Properties
        List<Lease> leases = leaseRepository.findByTenantId(tenant.getId());
        for (Lease l : leases) {
            String unitNum = l.getUnit() != null ? l.getUnit().getUnitNumber() : "";
            String propName = (l.getUnit() != null && l.getUnit().getProperty() != null)
                    ? l.getUnit().getProperty().getName() : "";

            boolean match = unitNum.toLowerCase().contains(term) || propName.toLowerCase().contains(term);
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("LEASE")
                        .categoryDisplayName("My Lease")
                        .title("Lease: " + propName + " (Unit " + unitNum + ")")
                        .subtitle("Rent: $" + (l.getMonthlyRent() != null ? l.getMonthlyRent() : "0.00"))
                        .badge(l.isActive() ? "ACTIVE" : "ENDED")
                        .badgeClass(l.isActive() ? "bg-success-subtle text-success" : "bg-secondary-subtle text-secondary")
                        .url("/leasing")
                        .icon("bi-file-earmark-text-fill")
                        .build());
            }
        }

        // 2. Tenant's own Payments
        List<Payment> payments = paymentRepository.findByTenantIdWithDetails(tenant.getId());
        for (Payment p : payments) {
            String ref = p.getTransactionReference() != null ? p.getTransactionReference() : "";
            String method = p.getPaymentMethod() != null ? p.getPaymentMethod() : "";

            boolean match = ref.toLowerCase().contains(term) || method.toLowerCase().contains(term);
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("PAYMENT")
                        .categoryDisplayName("My Payment")
                        .title(ref.isEmpty() ? "Payment #" + p.getId() : ref)
                        .subtitle("Amount: $" + (p.getAmount() != null ? p.getAmount() : "0.00"))
                        .badge(p.getStatus() != null ? p.getStatus().name() : "")
                        .badgeClass(p.getStatus() == PaymentStatus.PAID ? "bg-success-subtle text-success"
                                : (p.getStatus() == PaymentStatus.OVERDUE ? "bg-danger-subtle text-danger" : "bg-warning-subtle text-warning"))
                        .url("/payments")
                        .icon("bi-credit-card-2-front-fill")
                        .build());
            }
        }

        // 3. Tenant's own Maintenance Requests
        List<MaintenanceRequest> requests = maintenanceRequestRepository.findByTenantIdWithDetails(tenant.getId());
        for (MaintenanceRequest r : requests) {
            boolean match = (r.getTitle() != null && r.getTitle().toLowerCase().contains(term)) ||
                            (r.getCategory() != null && r.getCategory().toLowerCase().contains(term)) ||
                            ("REQ-" + r.getId()).toLowerCase().contains(term);
            if (match) {
                results.add(SearchResultDto.builder()
                        .category("MAINTENANCE")
                        .categoryDisplayName("Service Ticket")
                        .title("#REQ-" + r.getId() + " - " + r.getTitle())
                        .subtitle(r.getCategory() != null ? r.getCategory() : "Maintenance")
                        .badge(r.getStatus() != null ? r.getStatus().getDisplayName() : "")
                        .badgeClass("bg-primary-subtle text-primary")
                        .url("/maintenance/" + r.getId())
                        .icon("bi-tools")
                        .build());
            }
        }
    }
}

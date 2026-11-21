package com.propio.service;

import com.propio.dto.TenantCreationResult;
import com.propio.dto.TenantFormDto;
import com.propio.dto.TenantSummaryDto;
import com.propio.exception.TenantDeletionBlockedException;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing tenant profile lifecycle, user account generation,
 * and portfolio summaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final LeaseRepository leaseRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public Page<TenantSummaryDto> getTenantsPaged(String keyword, String filter, Pageable pageable) {
        Page<Tenant> page = tenantRepository.searchTenants(keyword != null ? keyword.trim() : "", pageable);

        List<TenantSummaryDto> dtos = page.getContent().stream()
                .map(this::mapToSummaryDto)
                .filter(dto -> matchesFilter(dto, filter))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    private boolean matchesFilter(TenantSummaryDto dto, String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) {
            return true;
        }
        if ("ACTIVE".equalsIgnoreCase(filter)) {
            return "Active".equalsIgnoreCase(dto.getLeaseStatus()) || "Expiring Soon".equalsIgnoreCase(dto.getLeaseStatus());
        }
        if ("OVERDUE".equalsIgnoreCase(filter)) {
            return dto.isOverdue() || "Overdue".equalsIgnoreCase(dto.getRentStatus());
        }
        if ("MOVED_OUT".equalsIgnoreCase(filter)) {
            return "Moved Out".equalsIgnoreCase(dto.getLeaseStatus()) || "No Lease".equalsIgnoreCase(dto.getLeaseStatus());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Tenant getTenantById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public TenantSummaryDto getTenantSummary(Long id) {
        Tenant tenant = getTenantById(id);
        return mapToSummaryDto(tenant);
    }

    @Transactional
    public TenantCreationResult createTenant(TenantFormDto dto) {
        String email = dto.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("A user account with email '" + email + "' already exists.");
        }
        if (tenantRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("A tenant profile with email '" + email + "' already exists.");
        }

        // 1. Generate secure temporary password
        String rawTempPassword = generateTemporaryPassword();

        // 2. Create User account with ROLE_TENANT
        String fullName = dto.getFirstName().trim() + " " + dto.getLastName().trim();
        String avatar = null;
        if (dto.getPhotoFile() != null && !dto.getPhotoFile().isEmpty()) {
            avatar = fileStorageService.storeTenantAvatar(dto.getPhotoFile());
        } else if (dto.getAvatarUrl() != null && !dto.getAvatarUrl().isBlank()) {
            avatar = dto.getAvatarUrl().trim();
        }

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .password(passwordEncoder.encode(rawTempPassword))
                .role(Role.ROLE_TENANT)
                .phone(dto.getPhone())
                .avatarUrl(avatar)
                .enabled(true)
                .build();
        user = userRepository.save(user);

        // 3. Create Tenant Profile linked to User
        Tenant tenant = Tenant.builder()
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .email(email)
                .phone(dto.getPhone().trim())
                .occupation(dto.getOccupation() != null ? dto.getOccupation().trim() : "")
                .emergencyContact(dto.getEmergencyContact() != null ? dto.getEmergencyContact().trim() : "")
                .avatarUrl(avatar)
                .user(user)
                .build();
        tenant = tenantRepository.save(tenant);

        log.info("Created new tenant '{}' with login user '{}'", tenant.getFullName(), user.getEmail());
        return new TenantCreationResult(tenant, rawTempPassword, user.getEmail());
    }

    @Transactional
    public Tenant updateTenant(Long id, TenantFormDto dto) {
        Tenant tenant = getTenantById(id);

        tenant.setFirstName(dto.getFirstName().trim());
        tenant.setLastName(dto.getLastName().trim());
        tenant.setPhone(dto.getPhone().trim());
        tenant.setOccupation(dto.getOccupation() != null ? dto.getOccupation().trim() : "");
        tenant.setEmergencyContact(dto.getEmergencyContact() != null ? dto.getEmergencyContact().trim() : "");

        if (dto.getPhotoFile() != null && !dto.getPhotoFile().isEmpty()) {
            String avatar = fileStorageService.storeTenantAvatar(dto.getPhotoFile());
            tenant.setAvatarUrl(avatar);
        } else if (dto.getAvatarUrl() != null) {
            tenant.setAvatarUrl(dto.getAvatarUrl().trim().isEmpty() ? null : dto.getAvatarUrl().trim());
        }

        // Sync with linked User if present
        if (tenant.getUser() != null) {
            User user = tenant.getUser();
            user.setFullName(tenant.getFullName());
            user.setPhone(tenant.getPhone());
            user.setAvatarUrl(tenant.getAvatarUrl());
            userRepository.save(user);
        }

        return tenantRepository.save(tenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = getTenantById(id);

        // Check for active leases
        long activeLeases = leaseRepository.findByTenantId(id).stream()
                .filter(Lease::isActive)
                .count();

        if (activeLeases > 0) {
            throw new TenantDeletionBlockedException("Cannot delete tenant '" + tenant.getFullName() +
                    "' because they currently have " + activeLeases + " active lease agreement(s). Terminate active leases first.");
        }

        User user = tenant.getUser();
        tenantRepository.delete(tenant);
        if (user != null) {
            userRepository.delete(user);
        }
        log.info("Deleted tenant ID: {} ({})", id, tenant.getFullName());
    }

    public TenantSummaryDto mapToSummaryDto(Tenant tenant) {
        List<Lease> activeLeases = leaseRepository.findActiveLeasesByTenantIdWithDetails(tenant.getId());
        Lease activeLease = activeLeases.isEmpty() ? null : activeLeases.get(0);
        List<Payment> payments = paymentRepository.findByLeaseTenantId(tenant.getId());
        if (payments == null) {
            payments = Collections.emptyList();
        }

        String currentUnit = "Unassigned";
        String propertyName = "No Property";
        String leaseStatus = "No Lease";
        Long activeLeaseId = null;

        if (activeLease != null) {
            activeLeaseId = activeLease.getId();
            if (activeLease.getUnit() != null) {
                currentUnit = activeLease.getUnit().getUnitNumber() != null ? activeLease.getUnit().getUnitNumber() : "Unit";
                if (activeLease.getUnit().getProperty() != null && activeLease.getUnit().getProperty().getName() != null) {
                    propertyName = activeLease.getUnit().getProperty().getName();
                }
            }
            if (activeLease.getEndDate() != null && !activeLease.getEndDate().isBefore(LocalDate.now())
                    && activeLease.getEndDate().isBefore(LocalDate.now().plusDays(60))) {
                leaseStatus = "Expiring Soon";
            } else {
                leaseStatus = "Active";
            }
        } else {
            // Check if they ever had a lease
            List<Lease> allLeases = leaseRepository.findByTenantId(tenant.getId());
            if (allLeases != null && !allLeases.isEmpty()) {
                leaseStatus = "Moved Out";
            }
        }

        BigDecimal balanceDue = payments.stream()
                .filter(p -> p != null && p.getStatus() != null && (p.getStatus() == PaymentStatus.OVERDUE || p.getStatus() == PaymentStatus.PENDING))
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean hasOverdue = payments.stream().anyMatch(p -> p != null && p.getStatus() == PaymentStatus.OVERDUE);
        String rentStatus = hasOverdue ? "Overdue" : (balanceDue.compareTo(BigDecimal.ZERO) > 0 ? "Pending" : "Up to date");

        String fullName = tenant.getFullName();
        String occupation = tenant.getOccupation() != null ? tenant.getOccupation() : "Resident";
        String phone = tenant.getPhone() != null ? tenant.getPhone() : "";

        return TenantSummaryDto.builder()
                .id(tenant.getId())
                .firstName(tenant.getFirstName() != null ? tenant.getFirstName() : "")
                .lastName(tenant.getLastName() != null ? tenant.getLastName() : "")
                .fullName(fullName)
                .email(tenant.getEmail() != null ? tenant.getEmail() : "")
                .phone(phone)
                .occupation(occupation)
                .avatarUrl(tenant.getAvatarUrl())
                .activeLeaseId(activeLeaseId)
                .currentUnitNumber(currentUnit)
                .propertyName(propertyName)
                .leaseStatus(leaseStatus)
                .rentStatus(rentStatus)
                .balanceDue(balanceDue)
                .overdue(hasOverdue)
                .hasLoginAccount(tenant.getUser() != null)
                .build();
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder("Propio@");
        for (int i = 0; i < 4; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        sb.append("!");
        return sb.toString();
    }
}

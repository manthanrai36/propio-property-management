package com.propio.service;

import com.propio.dto.ChangePasswordDto;
import com.propio.dto.RegisterDto;
import com.propio.dto.UpdateProfileDto;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.Role;
import com.propio.model.Tenant;
import com.propio.model.User;
import com.propio.repository.TenantRepository;
import com.propio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service managing user registration, profile updates, and credential management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User #" + userId + " not found."));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileDto dto) {
        User user = getUserById(userId);

        if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
            user.setFullName(dto.getFullName().trim());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().trim());
        }

        // Photo upload handling
        if (dto.getAvatar() != null && !dto.getAvatar().isEmpty()) {
            String avatarUrl = fileStorageService.storeTenantAvatar(dto.getAvatar());
            user.setAvatarUrl(avatarUrl);
            log.info("Updated profile avatar for user #{}: {}", userId, avatarUrl);
        }

        User saved = userRepository.save(user);

        // Synchronize with tenant profile if exists
        Optional<Tenant> tenantOpt = tenantRepository.findByUserId(userId);
        if (tenantOpt.isPresent()) {
            Tenant tenant = tenantOpt.get();
            String[] parts = saved.getFullName().split("\\s+", 2);
            tenant.setFirstName(parts[0]);
            tenant.setLastName(parts.length > 1 ? parts[1] : "");
            tenant.setPhone(saved.getPhone());
            if (saved.getAvatarUrl() != null) {
                tenant.setAvatarUrl(saved.getAvatarUrl());
            }
            tenantRepository.save(tenant);
        }

        log.info("Updated profile for user '{}' (ID: {})", saved.getEmail(), userId);
        return saved;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto) {
        User user = getUserById(userId);

        // Validate old/current password
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password does not match our records.");
        }

        // Validate confirmation
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation password do not match.");
        }

        if (dto.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters in length.");
        }

        // Hash new password with BCrypt
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        log.info("Successfully updated BCrypt hashed password for user '{}'", user.getEmail());
    }

    @Transactional
    public void updatePreferences(Long userId, UpdateProfileDto dto) {
        User user = getUserById(userId);

        if (dto.getThemePreference() != null && !dto.getThemePreference().isBlank()) {
            user.setThemePreference(dto.getThemePreference().trim().toLowerCase());
        }
        user.setNotifyRentDue(dto.isNotifyRentDue());
        user.setNotifyPaymentReceived(dto.isNotifyPaymentReceived());
        user.setNotifyMaintenance(dto.isNotifyMaintenance());
        user.setNotifyLeaseExpiry(dto.isNotifyLeaseExpiry());

        userRepository.save(user);
        log.info("Updated notification & theme preferences for user '{}'", user.getEmail());
    }

    @Transactional
    public User registerTenant(RegisterDto dto) {
        String cleanEmail = dto.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        User user = User.builder()
                .fullName(dto.getFullName().trim())
                .email(cleanEmail)
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.ROLE_TENANT)
                .phone(dto.getPhone())
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        // Automatically create a corresponding tenant record
        String[] nameParts = dto.getFullName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Tenant tenant = Tenant.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(cleanEmail)
                .phone(dto.getPhone())
                .user(savedUser)
                .build();

        tenantRepository.save(tenant);

        return savedUser;
    }
}

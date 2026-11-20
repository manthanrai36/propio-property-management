package com.propio.service;

import com.propio.dto.RegisterDto;
import com.propio.model.Role;
import com.propio.model.Tenant;
import com.propio.model.User;
import com.propio.repository.TenantRepository;
import com.propio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration and credential management.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

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

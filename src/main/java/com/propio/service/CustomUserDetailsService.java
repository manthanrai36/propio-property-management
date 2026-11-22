package com.propio.service;

import com.propio.config.CustomUserDetails;
import com.propio.model.Tenant;
import com.propio.model.User;
import com.propio.repository.TenantRepository;
import com.propio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to authenticate users against the database and enrich principal with role context.
 * Enforces lockout check when an account identifier has exceeded brute-force failure threshold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();

        // Check if account is temporarily locked due to excessive failed attempts
        if (loginAttemptService.isBlocked(cleanEmail)) {
            int remainingMins = loginAttemptService.getRemainingLockoutMinutes(cleanEmail);
            log.warn("Blocked login attempt: Account '{}' is locked for ~{} more minutes.", cleanEmail, remainingMins);
            throw new LockedException("Account is temporarily locked due to 5 consecutive failed login attempts. Please try again in 15 minutes.");
        }

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));

        Long tenantId = tenantRepository.findByUserId(user.getId())
                .map(Tenant::getId)
                .orElse(null);

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getFullName(),
                user.getRole(),
                user.getAvatarUrl(),
                tenantId,
                user.isEnabled()
        );
    }
}


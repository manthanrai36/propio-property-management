package com.propio.service;

import com.propio.config.CustomUserDetails;
import com.propio.model.Tenant;
import com.propio.model.User;
import com.propio.repository.TenantRepository;
import com.propio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to authenticate users against the database and enrich principal with role context.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
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

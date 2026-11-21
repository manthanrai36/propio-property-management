package com.propio.config;

import com.propio.model.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetails implementation holding rich user context (Name, Role, Avatar, Tenant ID).
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Role role;
    private final String avatarUrl;
    private final Long tenantId;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String email, String password, String fullName,
                             Role role, String avatarUrl, Long tenantId, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.avatarUrl = (avatarUrl != null && !avatarUrl.isBlank()) ? avatarUrl : null;
        this.tenantId = tenantId;
        this.enabled = enabled;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(role.name()));
    }

    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return (email != null && !email.isBlank()) ? email.substring(0, 1).toUpperCase() : "U";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        } else {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
    }

    public String getAvatarColor() {
        String name = (fullName != null && !fullName.isBlank()) ? fullName : ((email != null) ? email : "User");
        int hash = Math.abs(name.hashCode());
        String[] gradients = {
            "linear-gradient(135deg, #6C4CF1, #5131E5)",
            "linear-gradient(135deg, #3B82F6, #1D4ED8)",
            "linear-gradient(135deg, #06B6D4, #0E7490)",
            "linear-gradient(135deg, #10B981, #047857)",
            "linear-gradient(135deg, #F59E0B, #B45309)",
            "linear-gradient(135deg, #FF4B93, #BE123C)",
            "linear-gradient(135deg, #8B5CF6, #6D28D9)",
            "linear-gradient(135deg, #0D9488, #115E59)"
        };
        return gradients[hash % gradients.length];
    }

    public boolean isAdmin() {
        return this.role == Role.ROLE_ADMIN;
    }

    public boolean isTenant() {
        return this.role == Role.ROLE_TENANT;
    }

    public String getDisplayRole() {
        return this.role == Role.ROLE_ADMIN ? "Manager" : "Tenant";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

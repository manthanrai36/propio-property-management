package com.propio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * System User entity for authentication, profile management, and role authorization.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password;

    @NotNull(message = "User role is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "theme_preference", length = 20)
    private String themePreference = "system";

    @Builder.Default
    @Column(name = "notify_rent_due", nullable = false)
    private boolean notifyRentDue = true;

    @Builder.Default
    @Column(name = "notify_payment_received", nullable = false)
    private boolean notifyPaymentReceived = true;

    @Builder.Default
    @Column(name = "notify_maintenance", nullable = false)
    private boolean notifyMaintenance = true;

    @Builder.Default
    @Column(name = "notify_lease_expiry", nullable = false)
    private boolean notifyLeaseExpiry = true;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Tenant tenant;

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
}

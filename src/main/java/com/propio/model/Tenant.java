package com.propio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tenant profile entity linked to leases and optional portal user account.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Email
    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "emergency_contact", length = 100)
    private String emergencyContact;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private List<Lease> leases = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private List<MaintenanceRequest> maintenanceRequests = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private List<Document> documents = new ArrayList<>();

    public String getFullName() {
        String f = (firstName != null) ? firstName.trim() : "";
        String l = (lastName != null) ? lastName.trim() : "";
        String full = (f + " " + l).trim();
        return full.isEmpty() ? "Tenant" : full;
    }

    public String getInitials() {
        String f = (firstName != null && !firstName.isBlank()) ? firstName.substring(0, 1).toUpperCase() : "";
        String l = (lastName != null && !lastName.isBlank()) ? lastName.substring(0, 1).toUpperCase() : "";
        String combined = (f + l).trim();
        return combined.isEmpty() ? "T" : combined;
    }

    public String getAvatarColor() {
        String name = getFullName();
        if (name == null || name.isBlank() || "Tenant".equals(name)) {
            name = (email != null && !email.isBlank()) ? email : "Tenant";
        }
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

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
        return firstName + " " + lastName;
    }
}

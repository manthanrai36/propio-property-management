package com.propio.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * High-performance flat DTO used to render the tenant portfolio list.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSummaryDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String occupation;
    private String avatarUrl;
    private String initials;

    private Long activeLeaseId;
    private String currentUnitNumber;
    private String propertyName;
    private String leaseStatus; // "Active", "Expiring Soon", "Moved Out", "No Lease"
    private String rentStatus;  // "Up to date", "Overdue", "Pending"
    private BigDecimal balanceDue;
    private boolean hasLoginAccount;
    private boolean overdue;

    public String getInitials() {
        if (initials != null && !initials.isBlank()) {
            return initials;
        }
        String f = (firstName != null && !firstName.isBlank()) ? firstName.substring(0, 1).toUpperCase() : "";
        String l = (lastName != null && !lastName.isBlank()) ? lastName.substring(0, 1).toUpperCase() : "";
        return (f + l).isEmpty() ? "T" : (f + l);
    }

    public String getAvatarColor() {
        String name = (fullName != null && !fullName.isBlank()) ? fullName : ((email != null) ? email : "Tenant");
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

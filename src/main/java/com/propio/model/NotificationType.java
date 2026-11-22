package com.propio.model;

import lombok.Getter;

/**
 * Enumeration of notification types in Propio.
 */
@Getter
public enum NotificationType {
    PAYMENT_RECEIVED("Payment Received", "bi-credit-card-2-front-fill", "text-success", "bg-success-subtle"),
    RENT_OVERDUE("Rent Overdue", "bi-exclamation-triangle-fill", "text-danger", "bg-danger-subtle"),
    LEASE_EXPIRING("Lease Expiring", "bi-clock-history", "text-warning", "bg-warning-subtle"),
    MAINTENANCE_UPDATE("Maintenance Update", "bi-tools", "text-primary", "bg-primary-subtle"),
    SUPPORT("Support Inquiry", "bi-chat-dots-fill", "text-info", "bg-info-subtle"),
    SYSTEM("System Notification", "bi-bell-fill", "text-secondary", "bg-secondary-subtle");

    private final String displayName;
    private final String icon;
    private final String textColor;
    private final String bgColor;

    NotificationType(String displayName, String icon, String textColor, String bgColor) {
        this.displayName = displayName;
        this.icon = icon;
        this.textColor = textColor;
        this.bgColor = bgColor;
    }
}

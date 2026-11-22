package com.propio.model;

/**
 * Types of automated system notification emails dispatched by Propio.
 */
public enum EmailType {
    RENT_DUE_SOON("Rent Due Soon Reminder"),
    RENT_OVERDUE("Urgent: Overdue Rent Notice"),
    PAYMENT_RECEIVED("Payment Receipt Confirmation"),
    LEASE_EXPIRING("Lease Expiring Notice (60 Days)"),
    MAINTENANCE_UPDATE("Maintenance Request Update"),
    WELCOME_TENANT("Resident Portal Welcome"),
    TEST_EMAIL("System Test Email");

    private final String displayName;

    EmailType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package com.propio.model;

/**
 * Delivery status for an automated email message.
 */
public enum EmailStatus {
    SENT("Sent"),
    SIMULATED_SENT("Logged (Fallback)"),
    FAILED("Failed"),
    RETRYING("Retrying");

    private final String displayName;

    EmailStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

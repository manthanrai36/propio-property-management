package com.propio.model;

/**
 * Status of a tenant maintenance or service request.
 */
public enum RequestStatus {
    OPEN,
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public boolean isOpen() {
        return this == OPEN || this == PENDING;
    }

    public boolean isClosedOrResolved() {
        return this == RESOLVED || this == CLOSED || this == CANCELLED;
    }

    public String getDisplayName() {
        switch (this) {
            case OPEN:
            case PENDING:
                return "Open";
            case IN_PROGRESS:
                return "In Progress";
            case RESOLVED:
                return "Resolved";
            case CLOSED:
                return "Closed";
            case CANCELLED:
                return "Cancelled";
            default:
                return name();
        }
    }
}

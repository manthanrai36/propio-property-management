package com.propio.exception;

/**
 * Exception thrown when an attempt is made to delete a property that contains active leases.
 */
public class PropertyDeletionBlockedException extends RuntimeException {

    private final Long propertyId;
    private final String propertyName;
    private final long activeLeasesCount;

    public PropertyDeletionBlockedException(Long propertyId, String propertyName, long activeLeasesCount) {
        super(String.format("Cannot delete property '%s' because it currently has %d active tenant lease(s). Please terminate or transfer the leases before deleting.",
                propertyName, activeLeasesCount));
        this.propertyId = propertyId;
        this.propertyName = propertyName;
        this.activeLeasesCount = activeLeasesCount;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public long getActiveLeasesCount() {
        return activeLeasesCount;
    }
}

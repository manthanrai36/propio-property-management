package com.propio.exception;

/**
 * Exception thrown when an attempt is made to delete a tenant who currently has active leases.
 */
public class TenantDeletionBlockedException extends RuntimeException {

    public TenantDeletionBlockedException(String message) {
        super(message);
    }
}

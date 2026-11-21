package com.propio.exception;

/**
 * Thrown when an attempt is made to create or modify a lease that overlaps with
 * an existing active lease on the same property unit.
 */
public class LeaseOverlapException extends RuntimeException {

    public LeaseOverlapException(String message) {
        super(message);
    }
}

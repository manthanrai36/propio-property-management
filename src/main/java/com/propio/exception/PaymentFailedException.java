package com.propio.exception;

/**
 * Exception thrown when simulated online payment gateway rejects or declines a transaction.
 */
public class PaymentFailedException extends RuntimeException {

    private final Long paymentId;

    public PaymentFailedException(String message, Long paymentId) {
        super(message);
        this.paymentId = paymentId;
    }

    public Long getPaymentId() {
        return paymentId;
    }
}

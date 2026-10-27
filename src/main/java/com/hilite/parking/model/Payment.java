package com.hilite.parking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Demo payment record only; there is no gateway or real banking state. */
public record Payment(
        String paymentId,
        String sessionId,
        BigDecimal amount,
        PaymentStatus status,
        Instant confirmedAt
) {

    public Payment {
        requireText(paymentId, "paymentId");
        requireText(sessionId, "sessionId");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        Objects.requireNonNull(status, "status is required");

        if (status == PaymentStatus.CONFIRMED && confirmedAt == null) {
            throw new IllegalArgumentException("confirmedAt is required for a confirmed payment");
        }
        if (status == PaymentStatus.PENDING && confirmedAt != null) {
            throw new IllegalArgumentException("confirmedAt must be null for a pending payment");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

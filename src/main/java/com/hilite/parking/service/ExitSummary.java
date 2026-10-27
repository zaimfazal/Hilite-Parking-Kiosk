package com.hilite.parking.service;

import com.hilite.parking.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The saved outcome of a completed parking session, as shown on the exit success screen.
 *
 * <p>{@code paymentId} is {@code null} exactly when no demo payment was taken (a zero-fee
 * stay). A paid exit always carries the identifier of the confirmed payment row written in the
 * same transaction as the session completion.</p>
 */
public record ExitSummary(
        String sessionId,
        String registration,
        VehicleType vehicleType,
        Instant entryTime,
        Instant exitTime,
        Duration totalDuration,
        BigDecimal amountPaid,
        String paymentId
) {

    public ExitSummary {
        requireText(sessionId, "sessionId");
        requireText(registration, "registration");
        Objects.requireNonNull(vehicleType, "vehicleType is required");
        Objects.requireNonNull(entryTime, "entryTime is required");
        Objects.requireNonNull(exitTime, "exitTime is required");
        Objects.requireNonNull(totalDuration, "totalDuration is required");
        Objects.requireNonNull(amountPaid, "amountPaid is required");

        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime must not be before entryTime");
        }
        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException("totalDuration must not be negative");
        }
        if (amountPaid.signum() < 0) {
            throw new IllegalArgumentException("amountPaid must not be negative");
        }
        if (amountPaid.signum() == 0 && paymentId != null) {
            throw new IllegalArgumentException("a zero-fee exit has no payment id");
        }
        if (amountPaid.signum() > 0) {
            requireText(paymentId, "paymentId");
        }
    }

    /** True when a demo payment was recorded for this exit. */
    public boolean paid() {
        return amountPaid.signum() > 0;
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

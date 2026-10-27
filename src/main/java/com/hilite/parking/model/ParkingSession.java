package com.hilite.parking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One customer parking session. A session is active when both {@code exitTime}
 * and {@code finalFee} are null, and completed when both are present.
 *
 * <p>The registration must already be normalized by the caller using
 * {@code RegistrationUtil.normalize}; this model stores exactly what it is given
 * and does not import or call {@code RegistrationUtil}.
 *
 * <p>{@code entryTime} and {@code exitTime} are {@link Instant} event timestamps
 * in UTC. Conversion to local time belongs to the presentation layer.
 *
 * <p>{@code finalFee} is a non-negative rupee amount held as a {@link BigDecimal}.
 */
public record ParkingSession(
        String sessionId,
        String registration,
        VehicleType vehicleType,
        boolean chargingRequested,
        String slotId,
        Instant entryTime,
        Instant exitTime,
        BigDecimal finalFee
) {

    public ParkingSession {
        requireText(sessionId, "sessionId");
        requireText(registration, "registration");
        requireText(slotId, "slotId");
        Objects.requireNonNull(vehicleType, "vehicleType is required");
        Objects.requireNonNull(entryTime, "entryTime is required");

        if (exitTime != null && exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime must not be before entryTime");
        }
        if (finalFee != null && finalFee.signum() < 0) {
            throw new IllegalArgumentException("finalFee must not be negative");
        }
        if ((exitTime == null) != (finalFee == null)) {
            throw new IllegalArgumentException("exitTime and finalFee must be provided together");
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

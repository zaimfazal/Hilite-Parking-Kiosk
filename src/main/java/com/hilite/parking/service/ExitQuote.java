package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A parking-fee quote for one active session.
 *
 * <p>The quoted exit timestamp is captured once, when the quote is created. The amount due is
 * calculated from that exact instant and stored here, and confirming the exit reuses the same
 * timestamp. Time spent on the payment panel therefore cannot change what the customer was
 * shown.</p>
 *
 * <p>All instants are UTC {@link Instant} values; local-time formatting belongs to the
 * controller. Durations use the calculator's established precision: the total duration is the
 * exact elapsed time, while {@link #chargeableDuration()} is the whole number of billable hours
 * after the free allowance (zero for a free stay).</p>
 */
public record ExitQuote(
        String sessionId,
        String registration,
        VehicleType vehicleType,
        String slotId,
        Floor floor,
        Instant entryTime,
        Instant exitTime,
        Duration totalDuration,
        Duration freeAllowance,
        Duration chargeableDuration,
        BigDecimal hourlyRate,
        BigDecimal amountDue
) {

    public ExitQuote {
        requireText(sessionId, "sessionId");
        requireText(registration, "registration");
        Objects.requireNonNull(vehicleType, "vehicleType is required");
        requireText(slotId, "slotId");
        Objects.requireNonNull(floor, "floor is required");
        Objects.requireNonNull(entryTime, "entryTime is required");
        Objects.requireNonNull(exitTime, "exitTime is required");
        Objects.requireNonNull(totalDuration, "totalDuration is required");
        Objects.requireNonNull(freeAllowance, "freeAllowance is required");
        Objects.requireNonNull(chargeableDuration, "chargeableDuration is required");
        Objects.requireNonNull(hourlyRate, "hourlyRate is required");
        Objects.requireNonNull(amountDue, "amountDue is required");

        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime must not be before entryTime");
        }
        if (totalDuration.isNegative() || freeAllowance.isNegative()
                || chargeableDuration.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
        if (hourlyRate.signum() < 0) {
            throw new IllegalArgumentException("hourlyRate must not be negative");
        }
        if (amountDue.signum() < 0) {
            throw new IllegalArgumentException("amountDue must not be negative");
        }
    }

    /** True when the fee is greater than zero, so the demo payment step is required. */
    public boolean paymentRequired() {
        return amountDue.signum() > 0;
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

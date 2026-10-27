package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.VehicleType;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One active session that has been parked longer than {@link LongStayService#THRESHOLD}.
 *
 * @param elapsed whole seconds parked at the moment the alert list was built
 */
public record LongStayAlert(
        String sessionId,
        String registration,
        VehicleType vehicleType,
        Floor floor,
        String slotId,
        Instant entryTime,
        Duration elapsed,
        boolean chargingRequested
) {

    public LongStayAlert {
        requireText(sessionId, "sessionId");
        requireText(registration, "registration");
        Objects.requireNonNull(vehicleType, "vehicleType is required");
        Objects.requireNonNull(floor, "floor is required");
        requireText(slotId, "slotId");
        Objects.requireNonNull(entryTime, "entryTime is required");
        Objects.requireNonNull(elapsed, "elapsed is required");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

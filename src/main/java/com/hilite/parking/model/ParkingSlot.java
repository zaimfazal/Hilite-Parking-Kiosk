package com.hilite.parking.model;

import java.util.Objects;

/**
 * One physical bay in the fictional 24-bay layout (G-01..G-12, F1-01..F1-12).
 * Occupancy is not stored here; it must be derived from active sessions later.
 */
public record ParkingSlot(
        String slotId,
        Floor floor,
        int bayNumber,
        Zone zone,
        boolean charging
) {

    public ParkingSlot {
        Objects.requireNonNull(slotId, "slotId is required");
        if (slotId.isBlank()) {
            throw new IllegalArgumentException("slotId must not be blank");
        }
        Objects.requireNonNull(floor, "floor is required");
        Objects.requireNonNull(zone, "zone is required");
        if (bayNumber < 1) {
            throw new IllegalArgumentException("bayNumber must be at least 1");
        }
    }

    public String floorName() {
        return floor.displayName();
    }

    public String zoneName() {
        return zone.name();
    }
}

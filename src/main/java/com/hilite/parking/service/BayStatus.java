package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;

import java.util.Objects;

/**
 * One bay of the layout together with the active session occupying it, if any.
 *
 * @param slot          the bay
 * @param activeSession the active session in this bay, or {@code null} when the bay is free
 */
public record BayStatus(ParkingSlot slot, ParkingSession activeSession) {

    public BayStatus {
        Objects.requireNonNull(slot, "slot is required");
        if (activeSession != null) {
            if (activeSession.exitTime() != null) {
                throw new IllegalArgumentException("activeSession must not be completed");
            }
            if (!activeSession.slotId().equals(slot.slotId())) {
                throw new IllegalArgumentException("activeSession must belong to this slot");
            }
        }
    }

    /** True when an active session occupies this bay. */
    public boolean occupied() {
        return activeSession != null;
    }
}

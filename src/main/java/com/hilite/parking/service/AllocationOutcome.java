package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;

import java.util.Objects;

/**
 * The result of one entry-kiosk allocation attempt.
 *
 * <p>Allocation has exactly two possible outcomes, so it is modelled as a small closed type
 * instead of a nullable object or a boolean flag. A caller that switches on this type cannot
 * show a success screen after a rejected request, and a rejected request always carries a
 * reason that the kiosk can translate into one short sentence.</p>
 *
 * <p>Unexpected persistence failures are <strong>not</strong> represented here. They are
 * programming or environment problems, not parking outcomes, so the service lets
 * {@link com.hilite.parking.repository.DataAccessException} propagate and the controller
 * reports a generic failure. Domain outcomes (full car park, duplicate plate, and so on)
 * arrive as {@link Rejected}.</p>
 */
public sealed interface AllocationOutcome {

    /** Why a request could not be allocated. */
    enum Reason {

        /** The plate is empty, or does not match the demo plate format after normalization. */
        INVALID_REGISTRATION,

        /** The request itself is incomplete, for example a missing vehicle type. */
        INVALID_REQUEST,

        /** The normalized plate already has an active session. */
        DUPLICATE_ACTIVE_SESSION,

        /** No vacant bay of the required kind remains. */
        PARKING_FULL,

        /** Charging was requested but every charging bay is occupied. */
        CHARGING_UNAVAILABLE
    }

    /** A saved session together with the bay it occupies. */
    record Allocated(ParkingSession session, ParkingSlot slot) implements AllocationOutcome {

        public Allocated {
            Objects.requireNonNull(session, "session is required");
            Objects.requireNonNull(slot, "slot is required");
            if (!session.slotId().equals(slot.slotId())) {
                throw new IllegalArgumentException(
                        "session slot " + session.slotId() + " does not match slot " + slot.slotId());
            }
        }
    }

    /** A refused request: no session was created and no bay was changed. */
    record Rejected(Reason reason, String message) implements AllocationOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason is required");
            Objects.requireNonNull(message, "message is required");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }
}

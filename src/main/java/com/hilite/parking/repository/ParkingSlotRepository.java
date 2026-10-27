package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSlot;

import java.util.List;
import java.util.Optional;

/**
 * Read access to the fixed 24-bay layout.
 *
 * <p>The layout is seeded once by {@link DatabaseInitializer} and is not edited by the
 * application. Occupancy is deliberately absent: a bay is occupied exactly when an active
 * parking session references it, which is a session query, not a slot column.</p>
 */
public interface ParkingSlotRepository {

    /** Finds one slot by its identifier, for example {@code "G-03"}. */
    Optional<ParkingSlot> findById(String slotId);

    /** Returns all slots in deterministic order: Ground Floor first, then ascending bay. */
    List<ParkingSlot> findAll();

    /** Returns the charging-capable slots in the same deterministic order. */
    List<ParkingSlot> findCharging();

    /** Returns the number of rows in {@code parking_slots} (24 for a valid layout). */
    long count();
}

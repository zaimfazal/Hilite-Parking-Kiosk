package com.hilite.parking.service;

import com.hilite.parking.model.Floor;

import java.util.Objects;

/**
 * Occupancy of one floor at the moment a {@link DashboardStatistics} snapshot was taken.
 *
 * @param floor        the floor
 * @param totalBays    bays on this floor in the layout
 * @param occupiedBays bays on this floor holding an active session
 */
public record FloorOccupancy(Floor floor, int totalBays, int occupiedBays) {

    public FloorOccupancy {
        Objects.requireNonNull(floor, "floor is required");
        if (totalBays < 0) {
            throw new IllegalArgumentException("totalBays must not be negative");
        }
        if (occupiedBays < 0) {
            throw new IllegalArgumentException("occupiedBays must not be negative");
        }
        if (occupiedBays > totalBays) {
            throw new IllegalArgumentException("occupiedBays must not exceed totalBays");
        }
    }

    /** Bays on this floor without an active session. */
    public int availableBays() {
        return totalBays - occupiedBays;
    }
}

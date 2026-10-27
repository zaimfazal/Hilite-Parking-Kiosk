package com.hilite.parking.service;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable, internally consistent admin dashboard snapshot built by {@link StatisticsService}.
 *
 * <p>Entries count every stored session, exits count completed sessions, and occupancy counts
 * bays holding an active session. {@code floors} lists Ground Floor then First Floor;
 * {@code entriesByHour} has exactly 24 items for hours 0..23 in the reporting zone;
 * {@code bays} follows the layout order. All lists are unmodifiable copies.</p>
 *
 * @param peakHour hour with the most entries; empty when there are no entries, earliest hour
 *                 on a tie
 */
public record DashboardStatistics(
        long totalEntries,
        long totalExits,
        long carsEntered,
        long carsExited,
        long bikesEntered,
        long bikesExited,
        int totalBays,
        int occupiedBays,
        int availableBays,
        List<FloorOccupancy> floors,
        int chargingBays,
        int chargingOccupied,
        List<HourlyEntries> entriesByHour,
        OptionalInt peakHour,
        List<BayStatus> bays
) {

    /** Number of items in {@link #entriesByHour()}. */
    public static final int HOURS_PER_DAY = 24;

    public DashboardStatistics {
        requireNonNegative(totalEntries, "totalEntries");
        requireNonNegative(totalExits, "totalExits");
        requireNonNegative(carsEntered, "carsEntered");
        requireNonNegative(carsExited, "carsExited");
        requireNonNegative(bikesEntered, "bikesEntered");
        requireNonNegative(bikesExited, "bikesExited");
        requireNonNegative(totalBays, "totalBays");
        requireNonNegative(occupiedBays, "occupiedBays");
        requireNonNegative(availableBays, "availableBays");
        requireNonNegative(chargingBays, "chargingBays");
        requireNonNegative(chargingOccupied, "chargingOccupied");

        if (totalExits > totalEntries) {
            throw new IllegalArgumentException("totalExits must not exceed totalEntries");
        }
        if (occupiedBays + availableBays != totalBays) {
            throw new IllegalArgumentException(
                    "occupiedBays plus availableBays must equal totalBays");
        }
        if (chargingOccupied > chargingBays) {
            throw new IllegalArgumentException("chargingOccupied must not exceed chargingBays");
        }

        floors = List.copyOf(Objects.requireNonNull(floors, "floors is required"));
        entriesByHour = List.copyOf(
                Objects.requireNonNull(entriesByHour, "entriesByHour is required"));
        Objects.requireNonNull(peakHour, "peakHour is required");
        bays = List.copyOf(Objects.requireNonNull(bays, "bays is required"));

        if (entriesByHour.size() != HOURS_PER_DAY) {
            throw new IllegalArgumentException("entriesByHour must have exactly 24 items");
        }
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            if (entriesByHour.get(hour).hour() != hour) {
                throw new IllegalArgumentException("entriesByHour must be ordered by hour 0..23");
            }
        }
        if (peakHour.isPresent() && (peakHour.getAsInt() < 0 || peakHour.getAsInt() > 23)) {
            throw new IllegalArgumentException("peakHour must be between 0 and 23");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}

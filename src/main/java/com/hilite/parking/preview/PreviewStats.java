package com.hilite.parking.preview;

/** PHASE 1 PREVIEW ONLY. Dashboard figures that would later be derived from persisted sessions. */
public record PreviewStats(
        int occupancy,
        int available,
        int totalEntries,
        int totalExits,
        int carsEntered,
        int bikesEntered,
        int carsExited,
        int bikesExited,
        int evInUse,
        int evTotal,
        String peakHour,
        int peakHourEntries
) {
    public int totalBays() {
        return occupancy + available;
    }
}

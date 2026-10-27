package com.hilite.parking.service;

/**
 * Number of sessions whose entry time falls in one local hour of the day.
 *
 * @param hour    hour of day in the reporting zone, {@code 0..23}
 * @param entries number of sessions that entered during that hour (all days combined)
 */
public record HourlyEntries(int hour, long entries) {

    public HourlyEntries {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        if (entries < 0) {
            throw new IllegalArgumentException("entries must not be negative");
        }
    }
}

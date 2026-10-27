package com.hilite.parking.service;

import com.hilite.parking.model.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Protects the Phase 2 parking tariff: the first 120 elapsed minutes are free
 * (inclusive), any positive time beyond that is rounded up to whole hours, and
 * the hourly rate is Rs 5 for a car and Rs 2 for a bike.
 *
 * <p>All instants are fixed and derived from {@link #ENTRY}; no test depends on
 * the system clock, JavaFX, a database, or sleeping.</p>
 */
class FeeCalculatorTest {

    /** Fixed entry instant: 2026-10-25 08:00:00 UTC. */
    private static final Instant ENTRY = Instant.parse("2026-10-25T08:00:00Z");

    private final FeeCalculator calculator = new FeeCalculator();

    // ------------------------------------------------------------------
    // Deterministic helpers
    // ------------------------------------------------------------------

    private void assertFee(String expectedRupees, VehicleType vehicleType, Instant exitTime) {
        assertFee(expectedRupees, vehicleType, ENTRY, exitTime);
    }

    private void assertFee(String expectedRupees, VehicleType vehicleType,
                           Instant entryTime, Instant exitTime) {
        BigDecimal actual = calculator.calculateFee(vehicleType, entryTime, exitTime);
        assertNotNull(actual, "FeeCalculator must return a fee, never null");
        assertEquals(0, new BigDecimal(expectedRupees).compareTo(actual),
                "Expected Rs " + expectedRupees + " for " + vehicleType
                        + " but was Rs " + actual);
    }

    // ------------------------------------------------------------------
    // Required scenarios 1-4: zero duration and the inclusive 120-minute boundary
    // ------------------------------------------------------------------

    @Test
    void carZeroDurationIsFree() {
        assertFee("0", VehicleType.CAR, ENTRY);
    }

    @Test
    void bikeZeroDurationIsFree() {
        assertFee("0", VehicleType.BIKE, ENTRY);
    }

    @Test
    void carExactly120MinutesIsFree() {
        assertFee("0", VehicleType.CAR, ENTRY.plus(Duration.ofMinutes(120)));
    }

    @Test
    void bikeExactly120MinutesIsFree() {
        assertFee("0", VehicleType.BIKE, ENTRY.plus(Duration.ofMinutes(120)));
    }

    // ------------------------------------------------------------------
    // Required scenarios 5-6: one second past the free window starts billing
    // ------------------------------------------------------------------

    @Test
    void car120MinutesPlusOneSecondChargesOneHour() {
        assertFee("5", VehicleType.CAR,
                ENTRY.plus(Duration.ofMinutes(120).plusSeconds(1)));
    }

    @Test
    void bike120MinutesPlusOneSecondChargesOneHour() {
        assertFee("2", VehicleType.BIKE,
                ENTRY.plus(Duration.ofMinutes(120).plusSeconds(1)));
    }

    // ------------------------------------------------------------------
    // Required scenarios 7-8: partial hours round up (2h 20m -> 1 billable hour)
    // ------------------------------------------------------------------

    @Test
    void car2Hours20MinutesChargesOneHour() {
        assertFee("5", VehicleType.CAR,
                ENTRY.plus(Duration.ofHours(2).plusMinutes(20)));
    }

    @Test
    void bike2Hours20MinutesChargesOneHour() {
        assertFee("2", VehicleType.BIKE,
                ENTRY.plus(Duration.ofHours(2).plusMinutes(20)));
    }

    // ------------------------------------------------------------------
    // Required scenarios 9-10: exactly 3 hours is 1 billable hour
    // ------------------------------------------------------------------

    @Test
    void carExactly3HoursChargesOneHour() {
        assertFee("5", VehicleType.CAR, ENTRY.plus(Duration.ofHours(3)));
    }

    @Test
    void bikeExactly3HoursChargesOneHour() {
        assertFee("2", VehicleType.BIKE, ENTRY.plus(Duration.ofHours(3)));
    }

    // ------------------------------------------------------------------
    // Required scenarios 11-12: 3h 10m is 2 billable hours
    // ------------------------------------------------------------------

    @Test
    void car3Hours10MinutesChargesTwoHours() {
        assertFee("10", VehicleType.CAR,
                ENTRY.plus(Duration.ofHours(3).plusMinutes(10)));
    }

    @Test
    void bike3Hours10MinutesChargesTwoHours() {
        assertFee("4", VehicleType.BIKE,
                ENTRY.plus(Duration.ofHours(3).plusMinutes(10)));
    }

    // ------------------------------------------------------------------
    // Required scenarios 13-14: 4h 24m rounds up to 3 billable hours
    // ------------------------------------------------------------------

    @Test
    void car4Hours24MinutesChargesThreeHours() {
        assertFee("15", VehicleType.CAR,
                ENTRY.plus(Duration.ofHours(4).plusMinutes(24)));
    }

    @Test
    void bike4Hours24MinutesChargesThreeHours() {
        assertFee("6", VehicleType.BIKE,
                ENTRY.plus(Duration.ofHours(4).plusMinutes(24)));
    }

    // ------------------------------------------------------------------
    // Required scenario 15: exit before entry is rejected for both types
    // ------------------------------------------------------------------

    @Test
    void exitBeforeEntryIsRejectedForBothVehicleTypes() {
        Instant exitBeforeEntry = ENTRY.minusSeconds(1);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateFee(VehicleType.CAR, ENTRY, exitBeforeEntry),
                "Car exit before entry must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateFee(VehicleType.BIKE, ENTRY, exitBeforeEntry),
                "Bike exit before entry must be rejected");
    }

    // ------------------------------------------------------------------
    // Required scenario 16: null arguments are rejected
    // ------------------------------------------------------------------

    @Test
    void nullVehicleTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculateFee(null, ENTRY, ENTRY.plusSeconds(3600)));
    }

    @Test
    void nullEntryTimeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculateFee(VehicleType.CAR, null, ENTRY.plusSeconds(3600)));
    }

    @Test
    void nullExitTimeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculateFee(VehicleType.CAR, ENTRY, null));
    }

    // ------------------------------------------------------------------
    // Required scenario 17: 1h 30m is inside the free window
    // ------------------------------------------------------------------

    @Test
    void car90MinutesIsFree() {
        assertFee("0", VehicleType.CAR,
                ENTRY.plus(Duration.ofHours(1).plusMinutes(30)));
    }

    @Test
    void bike90MinutesIsFree() {
        assertFee("0", VehicleType.BIKE,
                ENTRY.plus(Duration.ofHours(1).plusMinutes(30)));
    }

    // ------------------------------------------------------------------
    // Required scenario 18: exactly 2h is still free
    // ------------------------------------------------------------------

    @Test
    void carExactly2HoursIsFree() {
        assertFee("0", VehicleType.CAR, ENTRY.plus(Duration.ofHours(2)));
    }

    @Test
    void bikeExactly2HoursIsFree() {
        assertFee("0", VehicleType.BIKE, ENTRY.plus(Duration.ofHours(2)));
    }

    // ------------------------------------------------------------------
    // Required scenario 19: sub-second time is truncated before billing
    // ------------------------------------------------------------------

    @Test
    void carHalfSecondOverFreeWindowIsStillFree() {
        assertFee("0", VehicleType.CAR,
                ENTRY.plus(Duration.ofMinutes(120).plusMillis(500)));
    }

    @Test
    void bikeHalfSecondOverFreeWindowIsStillFree() {
        assertFee("0", VehicleType.BIKE,
                ENTRY.plus(Duration.ofMinutes(120).plusMillis(500)));
    }

    // ------------------------------------------------------------------
    // Optional long-stay sanity case derived strictly from the frozen rule:
    // 24h car -> 22 billable hours x Rs 5 = Rs 110; bike -> 22 x Rs 2 = Rs 44.
    // ------------------------------------------------------------------

    @Test
    void car24HoursChargesTwentyTwoHours() {
        assertFee("110", VehicleType.CAR, ENTRY.plus(Duration.ofHours(24)));
    }

    @Test
    void bike24HoursChargesTwentyTwoHours() {
        assertFee("44", VehicleType.BIKE, ENTRY.plus(Duration.ofHours(24)));
    }
}

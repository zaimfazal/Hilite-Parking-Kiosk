package com.hilite.parking.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the Phase 2 {@link ParkingSession} record: an active session has no
 * exit time and no fee, a completed session has both, and invalid identity,
 * timing or money values are rejected. Persistence and uniqueness rules are
 * out of scope here (Phase 3).
 */
class ParkingSessionTest {

    private static final String SESSION_ID = "S-001";
    private static final String REGISTRATION = "KA01AB1234";
    private static final String SLOT_ID = "G-01";
    private static final Instant ENTRY = Instant.parse("2026-10-25T08:00:00Z");

    @Test
    void activeSessionWithNoExitOrFeeIsValid() {
        ParkingSession session = new ParkingSession(
                SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                SLOT_ID, ENTRY, null, null);

        assertEquals(SESSION_ID, session.sessionId());
        assertEquals(REGISTRATION, session.registration());
        assertEquals(VehicleType.CAR, session.vehicleType());
        assertFalse(session.chargingRequested());
        assertEquals(SLOT_ID, session.slotId());
        assertEquals(ENTRY, session.entryTime());
        assertNull(session.exitTime());
        assertNull(session.finalFee());
    }

    @Test
    void completedSessionWithZeroFeeIsValid() {
        Instant exit = ENTRY.plus(Duration.ofMinutes(30));
        ParkingSession session = new ParkingSession(
                SESSION_ID, REGISTRATION, VehicleType.BIKE, true,
                SLOT_ID, ENTRY, exit, BigDecimal.ZERO);

        assertEquals(exit, session.exitTime());
        assertEquals(0, BigDecimal.ZERO.compareTo(session.finalFee()));
        assertTrue(session.chargingRequested());
    }

    @Test
    void completedSessionWithPositiveFeeIsValid() {
        Instant exit = ENTRY.plus(Duration.ofHours(3));
        ParkingSession session = new ParkingSession(
                SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                SLOT_ID, ENTRY, exit, new BigDecimal("5.00"));

        assertEquals(exit, session.exitTime());
        assertEquals(0, new BigDecimal("5.00").compareTo(session.finalFee()));
    }

    @Test
    void nullSessionIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSession(null, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void emptySessionIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession("", REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void whitespaceSessionIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession("   ", REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void nullRegistrationIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSession(SESSION_ID, null, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void emptyRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, "", VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void whitespaceRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, "   ", VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void nullSlotIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        null, ENTRY, null, null));
    }

    @Test
    void emptySlotIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        "", ENTRY, null, null));
    }

    @Test
    void whitespaceSlotIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        "   ", ENTRY, null, null));
    }

    @Test
    void nullVehicleTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, null, false,
                        SLOT_ID, ENTRY, null, null));
    }

    @Test
    void nullEntryTimeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, null, null, null));
    }

    @Test
    void exitBeforeEntryIsRejected() {
        Instant exit = ENTRY.minusSeconds(1);

        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, exit, new BigDecimal("5.00")));
    }

    @Test
    void negativeFinalFeeIsRejected() {
        Instant exit = ENTRY.plus(Duration.ofHours(3));

        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, exit, new BigDecimal("-5.00")));
    }

    @Test
    void exitTimeWithoutFinalFeeIsRejected() {
        Instant exit = ENTRY.plus(Duration.ofHours(3));

        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, exit, null));
    }

    @Test
    void finalFeeWithoutExitTimeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession(SESSION_ID, REGISTRATION, VehicleType.CAR, false,
                        SLOT_ID, ENTRY, null, BigDecimal.ZERO));
    }
}

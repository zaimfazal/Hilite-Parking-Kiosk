package com.hilite.parking.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the Phase 2 {@link ParkingSlot} record: a slot must identify a bay,
 * floor and zone, and must reject blank IDs, missing floor/zone values and
 * bay numbers below 1. Display helpers are checked without pinning unrelated
 * formatting (for example whether the zone is rendered as "A" or "Zone A").
 */
class ParkingSlotTest {

    private static final String SLOT_ID = "G-01";

    @Test
    void validSlotExposesAllComponents() {
        ParkingSlot slot = new ParkingSlot(SLOT_ID, Floor.GROUND, 1, Zone.A, false);

        assertEquals(SLOT_ID, slot.slotId());
        assertEquals(Floor.GROUND, slot.floor());
        assertEquals(1, slot.bayNumber());
        assertEquals(Zone.A, slot.zone());
        assertFalse(slot.charging());
    }

    @Test
    void validChargingSlotKeepsChargingFlag() {
        ParkingSlot slot = new ParkingSlot("F1-05", Floor.FIRST, 5, Zone.A, true);

        assertEquals(Floor.FIRST, slot.floor());
        assertEquals(5, slot.bayNumber());
        assertTrue(slot.charging());
    }

    @Test
    void floorNameMatchesFloorDisplayName() {
        ParkingSlot ground = new ParkingSlot("G-01", Floor.GROUND, 1, Zone.A, false);
        ParkingSlot first = new ParkingSlot("F1-01", Floor.FIRST, 1, Zone.A, false);

        assertEquals(Floor.GROUND.displayName(), ground.floorName());
        assertEquals(Floor.FIRST.displayName(), first.floorName());
    }

    @Test
    void zoneNameReflectsTheSlotZone() {
        ParkingSlot zoneA = new ParkingSlot("G-01", Floor.GROUND, 1, Zone.A, false);
        ParkingSlot zoneB = new ParkingSlot("G-07", Floor.GROUND, 7, Zone.B, false);

        assertNotNull(zoneA.zoneName());
        assertNotNull(zoneB.zoneName());
        assertTrue(zoneA.zoneName().endsWith(Zone.A.name()),
                "Zone A slot should report zone A but was: " + zoneA.zoneName());
        assertTrue(zoneB.zoneName().endsWith(Zone.B.name()),
                "Zone B slot should report zone B but was: " + zoneB.zoneName());
    }

    @Test
    void nullSlotIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSlot(null, Floor.GROUND, 1, Zone.A, false));
    }

    @Test
    void emptySlotIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSlot("", Floor.GROUND, 1, Zone.A, false));
    }

    @Test
    void whitespaceSlotIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSlot("   ", Floor.GROUND, 1, Zone.A, false));
    }

    @Test
    void nullFloorIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSlot(SLOT_ID, null, 1, Zone.A, false));
    }

    @Test
    void nullZoneIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ParkingSlot(SLOT_ID, Floor.GROUND, 1, null, false));
    }

    @Test
    void zeroBayNumberIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSlot(SLOT_ID, Floor.GROUND, 0, Zone.A, false));
    }

    @Test
    void negativeBayNumberIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSlot(SLOT_ID, Floor.GROUND, -1, Zone.A, false));
    }
}

package com.hilite.parking.repository;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.Zone;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the read side of the fixed parking layout: 24 bays in deterministic
 * Ground-then-First order, correct enum/int mapping, and exactly four charging bays.
 */
class ParkingSlotRepositoryTest {

    @TempDir
    Path tempDir;

    private ParkingSlotRepository slots;

    @BeforeEach
    void setUp() {
        slots = new JdbcParkingSlotRepository(
                RepositoryTestSupport.initializedDatabase(tempDir));
    }

    // ------------------------------------------------------------------
    // Required scenario 11: deterministic Ground-then-First order
    // ------------------------------------------------------------------

    @Test
    void findAllReturnsTwentyFourSlotsGroundThenFirst() {
        List<ParkingSlot> all = slots.findAll();

        assertEquals(24, all.size());
        assertEquals(expectedOrder(),
                all.stream().map(ParkingSlot::slotId).toList(),
                "findAll must return G-01..G-12 followed by F1-01..F1-12");
        assertEquals("G-01", all.getFirst().slotId());
        assertEquals("F1-12", all.getLast().slotId());
    }

    // ------------------------------------------------------------------
    // Required scenario 12: id-to-column mapping for one slot per floor
    // ------------------------------------------------------------------

    @Test
    void findByIdMapsGroundAndFirstFloorSlots() {
        ParkingSlot ground = slots.findById("G-03").orElseThrow();
        assertEquals("G-03", ground.slotId());
        assertEquals(Floor.GROUND, ground.floor());
        assertEquals(3, ground.bayNumber());
        assertEquals(Zone.A, ground.zone());
        assertTrue(ground.charging());

        ParkingSlot first = slots.findById("F1-12").orElseThrow();
        assertEquals("F1-12", first.slotId());
        assertEquals(Floor.FIRST, first.floor());
        assertEquals(12, first.bayNumber());
        assertEquals(Zone.B, first.zone());
        assertTrue(first.charging());
    }

    // ------------------------------------------------------------------
    // Required scenario 13: exactly the four charging bays
    // ------------------------------------------------------------------

    @Test
    void findChargingReturnsExactlyTheFourConfiguredBays() {
        List<String> charging = slots.findCharging().stream()
                .map(ParkingSlot::slotId)
                .toList();

        assertEquals(List.of("G-03", "G-08", "F1-05", "F1-12"), charging);
    }

    // ------------------------------------------------------------------
    // Required scenario 14: count and unknown id
    // ------------------------------------------------------------------

    @Test
    void countIsTwentyFourAndUnknownIdIsEmpty() {
        assertEquals(24, slots.count());
        assertEquals(Optional.empty(), slots.findById("G-99"));
        assertEquals(Optional.empty(), slots.findById("g-03"),
                "Slot lookup must be case-sensitive");
    }

    private static List<String> expectedOrder() {
        List<String> ids = new ArrayList<>();
        for (int bay = 1; bay <= 12; bay++) {
            ids.add(String.format("G-%02d", bay));
        }
        for (int bay = 1; bay <= 12; bay++) {
            ids.add(String.format("F1-%02d", bay));
        }
        return ids;
    }
}

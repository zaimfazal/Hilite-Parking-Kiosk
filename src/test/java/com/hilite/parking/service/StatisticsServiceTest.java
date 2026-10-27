package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the admin dashboard snapshot against a real temporary SQLite database.
 *
 * <p>Sessions are seeded through the real session repository; the hour-bucket tests use fixed
 * UTC instants and compare two explicit reporting zones so the JVM default zone never matters.</p>
 */
class StatisticsServiceTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = AdminTestSupport.NOW;

    @TempDir
    Path tempDir;

    private AdminTestSupport.Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = AdminTestSupport.fixture(tempDir);
    }

    private DashboardStatistics snapshot(ZoneId zone) {
        return new StatisticsService(fixture.slots, fixture.sessions, zone).snapshot();
    }

    @Test
    void emptyDatabaseGivesZeroCountsAndFullAvailability() {
        DashboardStatistics stats = snapshot(UTC);

        assertEquals(0, stats.totalEntries());
        assertEquals(0, stats.totalExits());
        assertEquals(0, stats.carsEntered());
        assertEquals(0, stats.carsExited());
        assertEquals(0, stats.bikesEntered());
        assertEquals(0, stats.bikesExited());
        assertEquals(24, stats.totalBays());
        assertEquals(0, stats.occupiedBays());
        assertEquals(24, stats.availableBays());
        assertEquals(List.of(new FloorOccupancy(Floor.GROUND, 12, 0),
                new FloorOccupancy(Floor.FIRST, 12, 0)), stats.floors());
        assertEquals(4, stats.chargingBays());
        assertEquals(0, stats.chargingOccupied());
        assertEquals(24, stats.entriesByHour().size());
        for (int hour = 0; hour < 24; hour++) {
            assertEquals(new HourlyEntries(hour, 0), stats.entriesByHour().get(hour));
        }
        assertEquals(OptionalInt.empty(), stats.peakHour());
        assertEquals(24, stats.bays().size());
        assertTrue(stats.bays().stream().noneMatch(BayStatus::occupied));
    }

    @Test
    void mixedCarsAndBikesAreCountedExactly() {
        fixture.active("C-A1", "KA01AA0001", VehicleType.CAR, "G-01", NOW.minusSeconds(600));
        fixture.active("C-A2", "KA01AA0002", VehicleType.CAR, "F1-02", NOW.minusSeconds(700));
        fixture.completed("C-D1", "KA01AA0003", VehicleType.CAR, "G-02",
                NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        fixture.active("B-A1", "KA01BB0001", VehicleType.BIKE, "G-04", NOW.minusSeconds(800));
        fixture.completed("B-D1", "KA01BB0002", VehicleType.BIKE, "F1-03",
                NOW.minusSeconds(9000), NOW.minusSeconds(8000));
        fixture.completed("B-D2", "KA01BB0003", VehicleType.BIKE, "F1-03",
                NOW.minusSeconds(5000), NOW.minusSeconds(4000));

        DashboardStatistics stats = snapshot(UTC);

        assertEquals(6, stats.totalEntries());
        assertEquals(3, stats.totalExits());
        assertEquals(3, stats.carsEntered());
        assertEquals(1, stats.carsExited());
        assertEquals(3, stats.bikesEntered());
        assertEquals(2, stats.bikesExited());
        assertEquals(24, stats.totalBays());
        assertEquals(3, stats.occupiedBays());
        assertEquals(21, stats.availableBays());
    }

    @Test
    void perFloorOccupancyIsGroundThenFirst() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01", NOW.minusSeconds(60));
        fixture.active("S-2", "KA01AA0002", VehicleType.BIKE, "G-12", NOW.minusSeconds(60));
        fixture.active("S-3", "KA01AA0003", VehicleType.CAR, "F1-07", NOW.minusSeconds(60));
        fixture.completed("S-4", "KA01AA0004", VehicleType.CAR, "F1-08",
                NOW.minusSeconds(600), NOW.minusSeconds(300));

        DashboardStatistics stats = snapshot(UTC);

        assertEquals(List.of(new FloorOccupancy(Floor.GROUND, 12, 2),
                new FloorOccupancy(Floor.FIRST, 12, 1)), stats.floors());
        assertEquals(10, stats.floors().get(0).availableBays());
        assertEquals(11, stats.floors().get(1).availableBays());
    }

    @Test
    void chargingOccupancyCountsOnlyActiveSessionsOnChargingBays() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-03", NOW.minusSeconds(60), true);
        fixture.active("S-2", "KA01AA0002", VehicleType.CAR, "F1-12", NOW.minusSeconds(60));
        fixture.active("S-3", "KA01AA0003", VehicleType.CAR, "G-01", NOW.minusSeconds(60));
        fixture.completed("S-4", "KA01AA0004", VehicleType.CAR, "G-08",
                NOW.minusSeconds(600), NOW.minusSeconds(300));

        DashboardStatistics stats = snapshot(UTC);

        assertEquals(4, stats.chargingBays());
        assertEquals(2, stats.chargingOccupied());
        assertEquals(3, stats.occupiedBays());
    }

    @Test
    void hourlyBucketsUseTheConfiguredReportingZone() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                Instant.parse("2026-10-25T20:00:00Z"));
        fixture.active("S-2", "KA01AA0002", VehicleType.CAR, "G-02",
                Instant.parse("2026-10-25T20:30:00Z"));
        fixture.active("S-3", "KA01AA0003", VehicleType.BIKE, "G-04",
                Instant.parse("2026-10-26T03:00:00Z"));

        DashboardStatistics utc = snapshot(UTC);
        assertEquals(2, utc.entriesByHour().get(20).entries());
        assertEquals(1, utc.entriesByHour().get(3).entries());
        assertEquals(3, hourTotal(utc));
        assertEquals(OptionalInt.of(20), utc.peakHour());

        // +05:30: 20:00Z -> 01:30, 20:30Z -> 02:00, 03:00Z -> 08:30.
        DashboardStatistics kolkata = snapshot(KOLKATA);
        assertEquals(1, kolkata.entriesByHour().get(1).entries());
        assertEquals(1, kolkata.entriesByHour().get(2).entries());
        assertEquals(1, kolkata.entriesByHour().get(8).entries());
        assertEquals(0, kolkata.entriesByHour().get(20).entries());
        assertEquals(3, hourTotal(kolkata));
        assertEquals(OptionalInt.of(1), kolkata.peakHour());
    }

    @Test
    void peakHourTieGoesToTheEarliestHour() {
        fixture.completed("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                Instant.parse("2026-10-24T15:10:00Z"), Instant.parse("2026-10-24T15:40:00Z"));
        fixture.completed("S-2", "KA01AA0002", VehicleType.CAR, "G-01",
                Instant.parse("2026-10-25T15:20:00Z"), Instant.parse("2026-10-25T15:50:00Z"));
        fixture.completed("S-3", "KA01AA0003", VehicleType.CAR, "G-02",
                Instant.parse("2026-10-24T09:05:00Z"), Instant.parse("2026-10-24T09:30:00Z"));
        fixture.active("S-4", "KA01AA0004", VehicleType.BIKE, "G-02",
                Instant.parse("2026-10-26T09:59:59Z"));
        fixture.active("S-5", "KA01AA0005", VehicleType.CAR, "G-05",
                Instant.parse("2026-10-26T05:00:00Z"));

        DashboardStatistics stats = snapshot(UTC);

        assertEquals(2, stats.entriesByHour().get(9).entries());
        assertEquals(2, stats.entriesByHour().get(15).entries());
        assertEquals(1, stats.entriesByHour().get(5).entries());
        assertEquals(OptionalInt.of(9), stats.peakHour());
    }

    @Test
    void baysFollowLayoutOrderWithOccupiedFlags() {
        fixture.active("S-G05", "KA01AA0001", VehicleType.CAR, "G-05", NOW.minusSeconds(60));
        fixture.active("S-F107", "KA01AA0002", VehicleType.BIKE, "F1-07", NOW.minusSeconds(60));

        DashboardStatistics stats = snapshot(UTC);

        List<String> order = stats.bays().stream().map(bay -> bay.slot().slotId()).toList();
        assertEquals(ServiceTestSupport.ALL_SLOT_IDS, order);
        for (BayStatus bay : stats.bays()) {
            String slotId = bay.slot().slotId();
            switch (slotId) {
                case "G-05" -> assertEquals("S-G05", bay.activeSession().sessionId());
                case "F1-07" -> assertEquals("S-F107", bay.activeSession().sessionId());
                default -> {
                    assertFalse(bay.occupied(), slotId);
                    assertNull(bay.activeSession(), slotId);
                }
            }
        }
        assertEquals(2, stats.bays().stream().filter(BayStatus::occupied).count());
    }

    @Test
    void snapshotAfterARealExitFreesTheBay() {
        fixture.active("S-1", "KA01AB1234", VehicleType.CAR, "G-06",
                NOW.minus(Duration.ofHours(4)));
        assertTrue(snapshot(UTC).bays().get(5).occupied());

        ExitService exits = fixture.exitService(AdminTestSupport.FIXED_CLOCK);
        ExitQuote quote = assertInstanceOf(ExitOutcome.Quoted.class,
                exits.quote("KA01AB1234")).quote();
        assertInstanceOf(ExitOutcome.Completed.class, exits.confirmPayment(quote));

        DashboardStatistics stats = snapshot(UTC);
        assertEquals("G-06", stats.bays().get(5).slot().slotId());
        assertFalse(stats.bays().get(5).occupied());
        assertEquals(0, stats.occupiedBays());
        assertEquals(24, stats.availableBays());
        assertEquals(1, stats.totalEntries());
        assertEquals(1, stats.totalExits());
        assertEquals(1, stats.carsExited());
    }

    @Test
    void snapshotListsAreUnmodifiable() {
        DashboardStatistics stats = snapshot(UTC);

        assertThrows(UnsupportedOperationException.class, () -> stats.floors().clear());
        assertThrows(UnsupportedOperationException.class, () -> stats.entriesByHour().clear());
        assertThrows(UnsupportedOperationException.class, () -> stats.bays().clear());
    }

    @Test
    void recordsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new HourlyEntries(24, 0));
        assertThrows(IllegalArgumentException.class, () -> new HourlyEntries(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HourlyEntries(0, -1));
        assertThrows(NullPointerException.class, () -> new FloorOccupancy(null, 12, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new FloorOccupancy(Floor.GROUND, 12, 13));
        assertThrows(NullPointerException.class, () -> new BayStatus(null, null));

        DashboardStatistics valid = snapshot(UTC);
        List<HourlyEntries> tooShort = valid.entriesByHour().subList(0, 23);
        assertThrows(IllegalArgumentException.class, () -> new DashboardStatistics(
                0, 0, 0, 0, 0, 0, 24, 0, 24, valid.floors(), 4, 0,
                tooShort, OptionalInt.empty(), valid.bays()));
        assertThrows(NullPointerException.class, () -> new DashboardStatistics(
                0, 0, 0, 0, 0, 0, 24, 0, 24, null, 4, 0,
                valid.entriesByHour(), OptionalInt.empty(), valid.bays()));
        assertThrows(IllegalArgumentException.class, () -> new DashboardStatistics(
                -1, 0, 0, 0, 0, 0, 24, 0, 24, valid.floors(), 4, 0,
                valid.entriesByHour(), OptionalInt.empty(), valid.bays()));
    }

    @Test
    void nullConstructorArgumentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new StatisticsService(null, fixture.sessions, UTC));
        assertThrows(NullPointerException.class,
                () -> new StatisticsService(fixture.slots, null, UTC));
        assertThrows(NullPointerException.class,
                () -> new StatisticsService(fixture.slots, fixture.sessions, null));
    }

    private static long hourTotal(DashboardStatistics stats) {
        return stats.entriesByHour().stream().mapToLong(HourlyEntries::entries).sum();
    }
}

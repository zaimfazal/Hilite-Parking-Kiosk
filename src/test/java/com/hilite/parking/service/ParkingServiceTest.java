package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.model.Zone;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the frozen allocation rules end to end against a real temporary SQLite database.
 *
 * <p>Every test owns its own {@code @TempDir} database and injects one fixed clock, so the
 * chosen bay, the stored entry time, and the number of session rows are all deterministic.
 * Occupancy is seeded through the session repository, which means the tests exercise the same
 * definition of "occupied" that the service must use: an active session referencing the bay.</p>
 *
 * <p>Expected outcomes are asserted as concrete {@link AllocationOutcome} records, so a test
 * cannot pass on a generic success or a wrong rejection reason.</p>
 */
class ParkingServiceTest {

    private static final Instant ENTRY = ServiceTestSupport.FIXED_INSTANT;

    private static final List<String> ALL_SLOTS = ServiceTestSupport.ALL_SLOT_IDS;
    private static final List<String> CHARGING_SLOTS = ServiceTestSupport.CHARGING_SLOT_IDS;
    private static final List<String> REGULAR_SLOTS = ServiceTestSupport.REGULAR_SLOT_IDS;

    @TempDir
    Path tempDir;

    private Database database;
    private ParkingSlotRepository slots;
    private ParkingSessionRepository sessions;
    private ParkingService service;

    @BeforeEach
    void setUp() {
        database = ServiceTestSupport.initializedDatabase(tempDir);
        slots = new JdbcParkingSlotRepository(database);
        sessions = new JdbcParkingSessionRepository(database);
        service = ServiceTestSupport.newService(slots, sessions);
    }

    // ------------------------------------------------------------------
    // Successful allocation (scenarios 1-6)
    // ------------------------------------------------------------------

    @Test
    void carGetsFirstVacantRegularBay() {
        AllocationOutcome outcome = service.allocateParking("KA01AB1234", VehicleType.CAR, false);

        AllocationOutcome.Allocated result = allocated(outcome);
        assertEquals("G-01", result.slot().slotId());
        assertEquals(Floor.GROUND, result.slot().floor());
        assertEquals(Zone.A, result.slot().zone());
        assertFalse(result.slot().charging(), "G-01 must be a regular bay");
        assertEquals("G-01", result.session().slotId());
        assertEquals(1, sessions.countEntries());
        assertEquals(1, sessions.findActive().size());
    }

    @Test
    void bikeGetsFirstVacantRegularBay() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.BIKE, false));

        assertEquals("G-01", result.slot().slotId());

        ParkingSession stored = sessions.findById(result.session().sessionId()).orElseThrow();
        assertEquals(VehicleType.BIKE, stored.vehicleType());
        assertEquals("G-01", stored.slotId());
    }

    @Test
    void chargingCarGetsFirstChargingBay() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, true));

        assertEquals("G-03", result.slot().slotId());
        assertTrue(result.slot().charging(), "G-03 is the first charging bay");

        ParkingSession stored = sessions.findById(result.session().sessionId()).orElseThrow();
        assertTrue(stored.chargingRequested());
        assertEquals(VehicleType.CAR, stored.vehicleType());
    }

    @Test
    void chargingBikeGetsFirstChargingBay() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.BIKE, true));

        assertEquals("G-03", result.slot().slotId());

        ParkingSession stored = sessions.findById(result.session().sessionId()).orElseThrow();
        assertEquals(VehicleType.BIKE, stored.vehicleType());
        assertTrue(stored.chargingRequested());
    }

    @Test
    void entryTimeComesFromInjectedClock() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertEquals(ENTRY, result.session().entryTime(),
                "The session must use the injected clock, not the wall clock");

        ParkingSession stored = sessions.findById(result.session().sessionId()).orElseThrow();
        assertEquals(result.session(), stored,
                "The returned session must be the one persisted");
        assertEquals(ENTRY, stored.entryTime());
    }

    @Test
    void savedSessionHasNormalizedRegistrationAndAssignedBay() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("kl 11-ab 1234", VehicleType.CAR, false));

        String sessionId = result.session().sessionId();
        assertEquals("KL11AB1234", result.session().registration());

        ParkingSession stored = sessions.findById(sessionId).orElseThrow();
        assertEquals("KL11AB1234", stored.registration());
        assertEquals("G-01", stored.slotId());
        assertEquals("KL11AB1234", ServiceTestSupport.scalarString(database,
                "SELECT registration FROM parking_sessions WHERE session_id = ?", sessionId),
                "The stored column must hold the normalized plate");
        assertEquals("G-01", ServiceTestSupport.scalarString(database,
                "SELECT slot_id FROM parking_sessions WHERE session_id = ?", sessionId));
    }

    // ------------------------------------------------------------------
    // Charging and capacity (scenarios 7-11)
    // ------------------------------------------------------------------

    @Test
    void chargingFailsWhenAllChargingBaysOccupied() {
        ServiceTestSupport.occupySlots(database, CHARGING_SLOTS);
        long occupied = sessions.countEntries();
        assertEquals(4, occupied);

        AllocationOutcome outcome = service.allocateParking("KA01AB1234", VehicleType.CAR, true);

        rejected(outcome, AllocationOutcome.Reason.CHARGING_UNAVAILABLE);
        assertEquals(occupied, sessions.countEntries(),
                "A refused charging request must not add a session");
    }

    @Test
    void chargingFailureCreatesNoSession() {
        ServiceTestSupport.occupySlots(database, CHARGING_SLOTS);

        rejected(service.allocateParking("KA01AB1234", VehicleType.CAR, true),
                AllocationOutcome.Reason.CHARGING_UNAVAILABLE);

        assertEquals(4, sessions.countEntries(),
                "Only the four seeded occupiers may remain");
        assertTrue(sessions.findActiveByRegistration("KA01AB1234").isEmpty());
        assertTrue(sessions.findActiveBySlot("G-01").isEmpty());
        assertTrue(sessions.findActiveBySlot("G-02").isEmpty());
    }

    @Test
    void fullCarParkRejectsWithoutSession() {
        ServiceTestSupport.occupySlots(database, ALL_SLOTS);

        rejected(service.allocateParking("KA01AB1234", VehicleType.CAR, false),
                AllocationOutcome.Reason.PARKING_FULL);

        assertEquals(24, sessions.countEntries());
        assertTrue(sessions.findActiveByRegistration("KA01AB1234").isEmpty());
    }

    @Test
    void nonChargingPrefersRegularBays() {
        ServiceTestSupport.occupy(database, "G-01", "SEEDG01");

        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertEquals("G-02", result.slot().slotId());
        assertFalse(result.slot().charging(),
                "A non-charging request must use G-02 before any charging bay");
    }

    @Test
    void nonChargingUsesChargingBayWhenRegularBaysFull() {
        ServiceTestSupport.occupySlots(database, REGULAR_SLOTS);

        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertEquals("G-03", result.slot().slotId());
        assertTrue(result.slot().charging());
        assertEquals(21, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Duplicate registration (scenarios 12-15)
    // ------------------------------------------------------------------

    @Test
    void duplicateActiveRegistrationRejected() {
        allocated(service.allocateParking("KL11AB1234", VehicleType.CAR, false));
        long before = sessions.countEntries();

        rejected(service.allocateParking("kl 11 ab 1234", VehicleType.CAR, false),
                AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION);

        assertEquals(before, sessions.countEntries(),
                "The duplicate attempt must not insert another row");
    }

    @Test
    void duplicateLeavesOriginalSessionAndBayUnchanged() {
        AllocationOutcome.Allocated original = allocated(
                service.allocateParking("KL11AB1234", VehicleType.CAR, false));
        String sessionId = original.session().sessionId();
        String slotId = original.slot().slotId();

        rejected(service.allocateParking("kl 11 ab 1234", VehicleType.CAR, false),
                AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION);

        ParkingSession stored = sessions.findById(sessionId).orElseThrow();
        assertEquals(original.session(), stored);
        assertEquals(slotId, stored.slotId());
        assertEquals(1, sessions.countEntries());
        assertEquals(sessionId, sessions.findActiveBySlot(slotId).orElseThrow().sessionId());
    }

    @Test
    void normalizationIsConsistent() {
        allocated(service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        rejected(service.allocateParking("ka 01-ab 1234", VehicleType.CAR, false),
                AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION);

        assertEquals(1, sessions.countEntries());
        assertTrue(sessions.findActiveByRegistration("KA01AB1234").isPresent());
    }

    @Test
    void repeatedCallsCannotAllocateSamePlateTwice() {
        int allocatedCount = 0;
        int duplicateCount = 0;

        for (int attempt = 0; attempt < 5; attempt++) {
            AllocationOutcome outcome =
                    service.allocateParking("KA01AB1234", VehicleType.CAR, false);
            if (outcome instanceof AllocationOutcome.Allocated) {
                allocatedCount++;
            } else {
                rejected(outcome, AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION);
                duplicateCount++;
            }
        }

        assertEquals(1, allocatedCount);
        assertEquals(4, duplicateCount);
        assertEquals(1, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Persistence and safety (scenarios 16-20)
    // ------------------------------------------------------------------

    @Test
    void allocationSurvivesDatabaseReopen() {
        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        Database reopened = new Database(database.file());
        ParkingSessionRepository afterReopen = new JdbcParkingSessionRepository(reopened);

        ParkingSession loaded = afterReopen.findById(result.session().sessionId()).orElseThrow();
        assertEquals(result.slot().slotId(), loaded.slotId());
        assertEquals(ENTRY, loaded.entryTime());
        assertEquals(result.session(), loaded);
        assertEquals(1, afterReopen.countEntries());
    }

    @Test
    void databaseFailureDoesNotReportSuccess() {
        ServiceTestSupport.execute(database, """
                CREATE TRIGGER abort_session_insert
                BEFORE INSERT ON parking_sessions
                BEGIN
                    SELECT RAISE(ABORT, 'forced failure for allocation test');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> service.allocateParking("KA01AB1234", VehicleType.CAR, false),
                "A failed insert must propagate, never become a false Allocated result");

        assertEquals(0, sessions.countEntries());
        assertTrue(sessions.findActive().isEmpty());
    }

    @Test
    void failedAllocationDoesNotOccupyBays() {
        ServiceTestSupport.occupySlots(database, CHARGING_SLOTS);
        rejected(service.allocateParking("KA01AB1234", VehicleType.CAR, true),
                AllocationOutcome.Reason.CHARGING_UNAVAILABLE);
        assertTrue(sessions.findActiveByRegistration("KA01AB1234").isEmpty());

        ServiceTestSupport.occupySlots(database, REGULAR_SLOTS);
        rejected(service.allocateParking("KA02CD5678", VehicleType.CAR, false),
                AllocationOutcome.Reason.PARKING_FULL);
        assertTrue(sessions.findActiveByRegistration("KA02CD5678").isEmpty());

        assertEquals(24, sessions.countEntries());
        for (String slotId : ALL_SLOTS) {
            ParkingSession occupant = sessions.findActiveBySlot(slotId).orElseThrow(
                    () -> new AssertionError("Every bay should be occupied: " + slotId));
            assertNotEquals("KA01AB1234", occupant.registration());
            assertNotEquals("KA02CD5678", occupant.registration());
        }
    }

    @Test
    void orderIsDeterministicAcrossRuns() {
        List<String> plates = List.of(
                "KA01AB1234", "KA02CD5678", "KA03EF9012", "KA04GH3456", "KA05IJ7890");
        List<String> assigned = new ArrayList<>();

        for (String plate : plates) {
            AllocationOutcome.Allocated result = allocated(
                    service.allocateParking(plate, VehicleType.CAR, false));
            assigned.add(result.slot().slotId());
        }

        assertEquals(List.of("G-01", "G-02", "G-04", "G-05", "G-06"), assigned,
                "Bays must be assigned in layout order and skip charging bay G-03");
        assertEquals(5, sessions.countEntries());
    }

    @Test
    void slotTakenAfterSelectionIsRetried() {
        // A trigger aborts only the G-01 insert. Selection still starts at G-01, so this proves
        // the allocator retries the next candidate instead of reporting a false failure or
        // creating a second active session for G-01.
        ServiceTestSupport.execute(database, """
                CREATE TRIGGER abort_g01_insert
                BEFORE INSERT ON parking_sessions
                WHEN NEW.slot_id = 'G-01'
                BEGIN
                    SELECT RAISE(ABORT, 'simulated race for G-01');
                END
                """);

        AllocationOutcome.Allocated result = allocated(
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertEquals("G-02", result.slot().slotId());
        assertEquals(1, sessions.countEntries());
        assertEquals(result.session().sessionId(),
                sessions.findActiveBySlot("G-02").orElseThrow().sessionId());
        assertTrue(sessions.findActiveBySlot("G-01").isEmpty(),
                "The aborted G-01 insert must not leave an active session");
    }

    // ------------------------------------------------------------------
    // Input validation (scenarios 21-24)
    // ------------------------------------------------------------------

    @Test
    void nullVehicleTypeRejected() {
        AllocationOutcome outcome = assertDoesNotThrow(
                () -> service.allocateParking("KA01AB1234", null, false),
                "A missing vehicle type must be refused, not thrown as a NullPointerException");

        rejected(outcome, AllocationOutcome.Reason.INVALID_REQUEST);
        assertEquals(0, sessions.countEntries());
    }

    @Test
    void blankRegistrationRejected() {
        rejected(service.allocateParking("   ", VehicleType.CAR, false),
                AllocationOutcome.Reason.INVALID_REGISTRATION);

        assertEquals(0, sessions.countEntries());
    }

    @Test
    void nullRegistrationRejected() {
        AllocationOutcome outcome = assertDoesNotThrow(
                () -> service.allocateParking(null, VehicleType.CAR, false),
                "A null registration must be refused, not thrown as a NullPointerException");

        rejected(outcome, AllocationOutcome.Reason.INVALID_REGISTRATION);
        assertEquals(0, sessions.countEntries());
    }

    @Test
    void invalidPlateFormatRejected() {
        rejected(service.allocateParking("XYZ", VehicleType.CAR, false),
                AllocationOutcome.Reason.INVALID_REGISTRATION);

        assertEquals(0, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Assertion helpers
    // ------------------------------------------------------------------

    private static AllocationOutcome.Allocated allocated(AllocationOutcome outcome) {
        return assertInstanceOf(AllocationOutcome.Allocated.class, outcome);
    }

    private static AllocationOutcome.Rejected rejected(AllocationOutcome outcome,
                                                       AllocationOutcome.Reason reason) {
        AllocationOutcome.Rejected rejected =
                assertInstanceOf(AllocationOutcome.Rejected.class, outcome);
        assertEquals(reason, rejected.reason());
        return rejected;
    }
}

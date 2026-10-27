package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects parking-session persistence and its database-enforced invariants: every lookup path
 * agrees, active sessions are listed in entry-time order, duplicate active plates and bays are
 * impossible, completion releases the bay exactly once, and stored timestamps use the frozen
 * ISO-8601 UTC format.
 */
class ParkingSessionRepositoryTest {

    private static final Instant ENTRY = RepositoryTestSupport.ENTRY_TIME;

    @TempDir
    Path tempDir;

    private Database database;
    private ParkingSessionRepository sessions;

    @BeforeEach
    void setUp() {
        database = RepositoryTestSupport.initializedDatabase(tempDir);
        sessions = new JdbcParkingSessionRepository(database);
    }

    // ------------------------------------------------------------------
    // Required scenario 15: all lookup paths see an inserted active session
    // ------------------------------------------------------------------

    @Test
    void insertedActiveSessionIsVisibleThroughEveryLookup() {
        ParkingSession session = RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-07", ENTRY);

        sessions.insertActive(session);

        assertEquals(Optional.of(session), sessions.findById("S-001"));
        assertEquals(Optional.of(session), sessions.findActiveByRegistration("KA01AB1234"));
        assertEquals(Optional.of(session), sessions.findActiveBySlot("G-07"));
        assertEquals(List.of(session), sessions.findActive());
        assertEquals(List.of(session), sessions.findAll());
    }

    // ------------------------------------------------------------------
    // Required scenario 16: active list is ordered by entry time
    // ------------------------------------------------------------------

    @Test
    void activeSessionsAreOrderedByEntryTime() {
        ParkingSession later = RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY.plus(Duration.ofMinutes(10)));
        ParkingSession earlier = RepositoryTestSupport.activeCar(
                "S-002", "KA02CD5678", "G-02", ENTRY);

        sessions.insertActive(later);
        sessions.insertActive(earlier);

        assertEquals(List.of("S-002", "S-001"),
                RepositoryTestSupport.sessionIds(sessions.findActive()),
                "Active sessions must come back in entry-time order, not id order");
        assertEquals(List.of("S-002", "S-001"),
                RepositoryTestSupport.sessionIds(sessions.findAll()));
    }

    // ------------------------------------------------------------------
    // Required scenario 17: entry and completed-exit counts
    // ------------------------------------------------------------------

    @Test
    void countsTrackEntriesAndCompletedExits() {
        assertEquals(0, sessions.countEntries());
        assertEquals(0, sessions.countCompletedExits());

        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY));
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-002", "KA02CD5678", "G-02", ENTRY));
        sessions.updateCompleted(RepositoryTestSupport.completedCar(
                "S-002", "KA02CD5678", "G-02",
                ENTRY, ENTRY.plus(Duration.ofHours(3)), "15.00"));

        assertEquals(2, sessions.countEntries());
        assertEquals(1, sessions.countCompletedExits());
    }

    // ------------------------------------------------------------------
    // Required scenario 18: one active session per normalized registration
    // ------------------------------------------------------------------

    @Test
    void duplicateActiveRegistrationIsRejectedAndLeavesOneRow() {
        ParkingSession first = RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY);
        sessions.insertActive(first);

        assertThrows(DataAccessException.class, () -> sessions.insertActive(
                        RepositoryTestSupport.activeCar(
                                "S-002", "KA01AB1234", "G-02", ENTRY.plusSeconds(1))),
                "A second active session for the same plate must be rejected");

        assertEquals(1, sessions.countEntries());
        assertEquals(Optional.of("S-001"),
                sessions.findActiveByRegistration("KA01AB1234")
                        .map(ParkingSession::sessionId));
        assertTrue(sessions.findById("S-002").isEmpty());
    }

    // ------------------------------------------------------------------
    // Required scenario 19: one active session per bay
    // ------------------------------------------------------------------

    @Test
    void duplicateActiveSlotIsRejectedForDifferentRegistration() {
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY));

        assertThrows(DataAccessException.class, () -> sessions.insertActive(
                        RepositoryTestSupport.activeCar(
                                "S-002", "KA02CD5678", "G-01", ENTRY.plusSeconds(1))),
                "A second active session for the same bay must be rejected");

        assertEquals(1, sessions.countEntries());
        assertEquals(Optional.of("S-001"),
                sessions.findActiveBySlot("G-01").map(ParkingSession::sessionId));
    }

    // ------------------------------------------------------------------
    // Required scenario 20: completion frees both plate and bay
    // ------------------------------------------------------------------

    @Test
    void completingSessionFreesRegistrationAndSlotForReuse() {
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY));
        ParkingSession completed = RepositoryTestSupport.completedCar(
                "S-001", "KA01AB1234", "G-01",
                ENTRY, ENTRY.plus(Duration.ofHours(3)), "15.00");

        sessions.updateCompleted(completed);

        assertTrue(sessions.findActiveBySlot("G-01").isEmpty());
        assertTrue(sessions.findActiveByRegistration("KA01AB1234").isEmpty());
        assertEquals(Optional.of(completed), sessions.findById("S-001"));

        ParkingSession reused = RepositoryTestSupport.activeCar(
                "S-002", "KA01AB1234", "G-01", ENTRY.plus(Duration.ofHours(4)));
        sessions.insertActive(reused);

        assertEquals(Optional.of(reused), sessions.findActiveByRegistration("KA01AB1234"));
        assertEquals(Optional.of(reused), sessions.findActiveBySlot("G-01"));
        assertEquals(2, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Required scenario 21: no double completion and no double charge
    // ------------------------------------------------------------------

    @Test
    void secondCompletionOfSameSessionIsRejected() {
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY));
        ParkingSession completed = RepositoryTestSupport.completedCar(
                "S-001", "KA01AB1234", "G-01",
                ENTRY, ENTRY.plus(Duration.ofHours(3)), "15.00");
        sessions.updateCompleted(completed);

        ParkingSession conflicting = RepositoryTestSupport.completedCar(
                "S-001", "KA01AB1234", "G-01",
                ENTRY, ENTRY.plus(Duration.ofHours(5)), "25.00");
        assertThrows(DataAccessException.class,
                () -> sessions.updateCompleted(conflicting),
                "A completed session must not be completed a second time");

        ParkingSession stored = sessions.findById("S-001").orElseThrow();
        assertEquals(completed.exitTime(), stored.exitTime());
        assertEquals(0, new BigDecimal("15.00").compareTo(stored.finalFee()),
                "The first completion must remain the only recorded charge");
        assertEquals(1, sessions.countCompletedExits());
    }

    // ------------------------------------------------------------------
    // Required scenario 22: repository-side input validation
    // ------------------------------------------------------------------

    @Test
    void insertActiveRejectsCompletedSessionAndNonNormalizedRegistration() {
        ParkingSession completed = RepositoryTestSupport.completedCar(
                "S-001", "KA01AB1234", "G-01",
                ENTRY, ENTRY.plus(Duration.ofHours(3)), "15.00");
        assertThrows(IllegalArgumentException.class,
                () -> sessions.insertActive(completed),
                "insertActive must only accept active sessions");

        ParkingSession nonNormalized = RepositoryTestSupport.activeCar(
                "S-002", "ka 01 ab 1234", "G-02", ENTRY);
        assertThrows(IllegalArgumentException.class,
                () -> sessions.insertActive(nonNormalized),
                "insertActive must reject a registration that is not normalized");

        assertEquals(0, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Required scenario 23: invalid completion pairings are rejected
    // ------------------------------------------------------------------

    @Test
    void invalidCompletionPairingsAreRejected() throws SQLException {
        Instant exit = ENTRY.plus(Duration.ofHours(3));

        assertThrows(IllegalArgumentException.class,
                () -> new ParkingSession("S-MODEL", "KA01AB1234", VehicleType.CAR, false,
                        "G-01", ENTRY, exit, null),
                "The model must require a fee together with an exit time");

        assertThrows(SQLException.class,
                () -> RepositoryTestSupport.insertRawSession(
                        database, "S-RAW-1", "KA02CD5678", "G-02", ENTRY, exit, null),
                "exit_time without final_fee_paise must violate a CHECK constraint");

        assertThrows(SQLException.class,
                () -> RepositoryTestSupport.insertRawSession(
                        database, "S-RAW-2", "KA03EF9012", "G-03",
                        ENTRY, ENTRY.minusSeconds(1), 500L),
                "exit_time before entry_time must violate a CHECK constraint");

        assertEquals(0, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Required scenario 24: slot foreign key
    // ------------------------------------------------------------------

    @Test
    void sessionWithUnknownSlotIsRejected() {
        assertThrows(DataAccessException.class,
                () -> sessions.insertActive(RepositoryTestSupport.activeCar(
                        "S-001", "KA01AB1234", "G-99", ENTRY)),
                "A session must reference an existing slot");

        assertEquals(0, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Required scenario 25: canonical stored timestamp
    // ------------------------------------------------------------------

    @Test
    void storedEntryTimeIsCanonicalIsoUtcWithNanoseconds() {
        Instant entry = Instant.parse("2026-10-25T08:00:00.123456789Z");
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", entry));

        String stored = RepositoryTestSupport.scalarString(database,
                "SELECT entry_time FROM parking_sessions WHERE session_id = ?", "S-001");

        assertEquals("2026-10-25T08:00:00.123456789Z", stored);
        assertEquals(30, stored.length(), "Stored timestamp must be exactly 30 characters");
        assertTrue(stored.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{9}Z"),
                "Stored timestamp must be ISO-8601 UTC with nine fractional digits: " + stored);
    }

    // ------------------------------------------------------------------
    // Required scenario 26: completed fee survives a restart
    // ------------------------------------------------------------------

    @Test
    void completedSessionAndFeeSurviveDatabaseReopen() {
        sessions.insertActive(RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-01", ENTRY));
        ParkingSession completed = RepositoryTestSupport.completedCar(
                "S-001", "KA01AB1234", "G-01",
                ENTRY, ENTRY.plus(Duration.ofHours(4).plusMinutes(24)), "15.00");
        sessions.updateCompleted(completed);

        Database reopened = new Database(database.file());
        ParkingSessionRepository afterRestart = new JdbcParkingSessionRepository(reopened);

        assertEquals(1, afterRestart.countEntries());
        assertEquals(1, afterRestart.countCompletedExits());
        ParkingSession loaded = afterRestart.findById("S-001").orElseThrow();
        assertEquals(completed.exitTime(), loaded.exitTime());
        assertEquals(0, completed.finalFee().compareTo(loaded.finalFee()),
                "The stored fee must round-trip exactly");
        assertTrue(afterRestart.findActive().isEmpty());
    }
}

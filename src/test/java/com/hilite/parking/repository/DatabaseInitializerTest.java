package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects Phase 3 schema creation and seeding: four tables and the three partial unique
 * indexes exist, the fixed 24-bay layout is written exactly once, re-initialization is
 * non-destructive, and a failed seed rolls back instead of leaving a partial layout.
 *
 * <p>Every test uses a fresh database under {@link TempDir}; the runtime file
 * {@code data/hilite-parking.db} is never touched.</p>
 */
class DatabaseInitializerTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void initializeFreshDatabase() {
        database = RepositoryTestSupport.initializedDatabase(tempDir);
    }

    // ------------------------------------------------------------------
    // Required scenario 1: tables and indexes
    // ------------------------------------------------------------------

    @Test
    void firstInitializeCreatesTablesAndRequiredIndexes() {
        Set<String> tables = new HashSet<>(RepositoryTestSupport.stringColumn(database,
                "SELECT name FROM sqlite_master WHERE type = 'table'"));
        assertTrue(tables.containsAll(Set.of(
                        "parking_slots", "parking_sessions", "payments", "admin_settings")),
                "Missing required tables; found " + tables);

        Set<String> indexes = new HashSet<>(RepositoryTestSupport.stringColumn(database,
                "SELECT name FROM sqlite_master WHERE type = 'index'"));
        assertTrue(indexes.containsAll(Set.of(
                        "ux_sessions_active_registration",
                        "ux_sessions_active_slot",
                        "ux_payments_confirmed_session")),
                "Missing required indexes; found " + indexes);
    }

    // ------------------------------------------------------------------
    // Required scenario 2: exactly 24 slots with the frozen ids
    // ------------------------------------------------------------------

    @Test
    void firstInitializeSeedsExactlyTwentyFourSlots() {
        List<String> ids = RepositoryTestSupport.stringColumn(database,
                "SELECT slot_id FROM parking_slots");

        assertEquals(24, ids.size(), "The layout must always contain 24 slots");
        assertEquals(expectedSlotIds(), new HashSet<>(ids),
                "Slot ids must be G-01..G-12 and F1-01..F1-12");
        assertEquals(24, new HashSet<>(ids).size(), "Slot ids must be unique");
    }

    // ------------------------------------------------------------------
    // Required scenario 3: exactly four charging bays
    // ------------------------------------------------------------------

    @Test
    void seedingMarksExactlyFourChargingSlots() {
        List<String> charging = RepositoryTestSupport.stringColumn(database,
                "SELECT slot_id FROM parking_slots WHERE charging = 1");

        assertEquals(4, charging.size(), "Exactly four bays may be charging-capable");
        assertEquals(Set.of("G-03", "G-08", "F1-05", "F1-12"),
                new HashSet<>(charging),
                "Charging bays must be G-03, G-08, F1-05 and F1-12");
    }

    // ------------------------------------------------------------------
    // Required scenario 4: sampled floor/bay/zone/charging mapping
    // ------------------------------------------------------------------

    @Test
    void seededRowsHaveExpectedFloorBayZoneAndChargingMapping() {
        assertSlot("G-01", "GROUND", 1, "A", false);
        assertSlot("G-06", "GROUND", 6, "A", false);
        assertSlot("G-07", "GROUND", 7, "B", false);
        assertSlot("G-12", "GROUND", 12, "B", false);
        assertSlot("F1-05", "FIRST", 5, "A", true);
        assertSlot("F1-12", "FIRST", 12, "B", true);
    }

    // ------------------------------------------------------------------
    // Required scenario 5: idempotent reseeding preserves a changed setting
    // ------------------------------------------------------------------

    @Test
    void reinitializingIsIdempotentAndPreservesChangedSetting() {
        JdbcSettingsRepository settings = new JdbcSettingsRepository(database);
        settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "9876");

        new DatabaseInitializer(database).initialize();

        JdbcSettingsRepository afterReinitialize = new JdbcSettingsRepository(database);
        assertEquals(Optional.of("9876"),
                afterReinitialize.findValue(SettingsRepository.KEY_ADMIN_PIN),
                "Seeding must not overwrite an existing admin PIN");
        assertEquals(24, new JdbcParkingSlotRepository(database).count(),
                "Re-initialization must not duplicate slots");
        assertEquals(24, RepositoryTestSupport.scalarLong(database,
                "SELECT COUNT(*) FROM parking_slots"));
    }

    // ------------------------------------------------------------------
    // Required scenario 6: sessions survive re-initialization and reopening
    // ------------------------------------------------------------------

    @Test
    void existingSessionsSurviveReinitializationAndReopening() {
        ParkingSession session = RepositoryTestSupport.activeCar(
                "S-001", "KA01AB1234", "G-07", RepositoryTestSupport.ENTRY_TIME);
        new JdbcParkingSessionRepository(database).insertActive(session);

        new DatabaseInitializer(database).initialize();

        Database reopened = new Database(database.file());
        JdbcParkingSessionRepository sessions = new JdbcParkingSessionRepository(reopened);
        assertEquals(1, sessions.countEntries(), "Re-initialization must keep existing sessions");
        assertEquals(Optional.of(session), sessions.findById("S-001"));
        assertEquals(Optional.of(session), sessions.findActiveByRegistration("KA01AB1234"));
    }

    // ------------------------------------------------------------------
    // Required scenario 7: an incomplete layout is reported, never repaired
    // ------------------------------------------------------------------

    @Test
    void incompleteExistingLayoutFailsWithoutRepair() {
        RepositoryTestSupport.executeSql(database,
                "DELETE FROM parking_slots WHERE slot_id = 'G-12'");

        assertThrows(DataAccessException.class,
                () -> new DatabaseInitializer(database).initialize(),
                "An incomplete layout must fail loudly instead of being silently repaired");

        JdbcParkingSlotRepository slots = new JdbcParkingSlotRepository(database);
        assertEquals(23, slots.count(), "The failed initialization must not add rows");
        assertTrue(slots.findById("G-12").isEmpty(), "The deleted slot must stay deleted");
    }

    // ------------------------------------------------------------------
    // Required scenario 8: failed seeding is fully transactional
    // ------------------------------------------------------------------

    @Test
    void failedSeedingRollsBackWithoutPartialRows() {
        RepositoryTestSupport.executeSql(database, "DELETE FROM parking_slots");
        RepositoryTestSupport.executeSql(database, """
                CREATE TRIGGER block_g05 BEFORE INSERT ON parking_slots
                WHEN NEW.slot_id = 'G-05'
                BEGIN
                    SELECT RAISE(ABORT, 'test-only failure for G-05');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> new DatabaseInitializer(database).initialize(),
                "A seeding failure must surface as DataAccessException");

        assertEquals(0, new JdbcParkingSlotRepository(database).count(),
                "Seeding must roll back instead of leaving a partial layout");
        assertEquals(0, RepositoryTestSupport.scalarLong(database,
                "SELECT COUNT(*) FROM parking_slots"));
    }

    // ------------------------------------------------------------------
    // Required scenario 9: foreign keys are enabled on every connection
    // ------------------------------------------------------------------

    @Test
    void foreignKeysAreEnabledOnEveryConnection() throws SQLException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Connection connection = database.openConnection()) {
                assertEquals(1, RepositoryTestSupport.scalarLong(connection, "PRAGMA foreign_keys"),
                        "foreign_keys must be ON for connection " + attempt);
            }
        }

        Database reopened = new Database(database.file());
        try (Connection connection = reopened.openConnection()) {
            assertEquals(1, RepositoryTestSupport.scalarLong(connection, "PRAGMA foreign_keys"),
                    "foreign_keys must be ON after reopening the file");
        }
    }

    // ------------------------------------------------------------------
    // Required scenario 10: the test database stays inside @TempDir
    // ------------------------------------------------------------------

    @Test
    void testDatabaseFileLivesUnderTempDir() {
        Path file = database.file().toAbsolutePath().normalize();
        Path tempRoot = tempDir.toAbsolutePath().normalize();

        assertEquals(tempRoot.resolve("test.db"), file);
        assertTrue(file.startsWith(tempRoot), "Test database must stay inside @TempDir");
        assertTrue(Files.exists(file), "The initializer must create the database file");
        assertNotEquals(Database.DEFAULT_FILE.toAbsolutePath().normalize(), file,
                "Tests must never use data/hilite-parking.db");
        assertFalse(file.startsWith(Path.of("").toAbsolutePath().normalize().resolve("data")),
                "Tests must never write into the project data directory");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<String> expectedSlotIds() {
        Set<String> expected = new LinkedHashSet<>();
        for (int bay = 1; bay <= 12; bay++) {
            expected.add(String.format("G-%02d", bay));
        }
        for (int bay = 1; bay <= 12; bay++) {
            expected.add(String.format("F1-%02d", bay));
        }
        return expected;
    }

    private void assertSlot(String slotId, String floor, int bayNumber,
                            String zone, boolean charging) {
        String sql = "SELECT floor, bay_number, zone, charging "
                + "FROM parking_slots WHERE slot_id = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Missing slot " + slotId);
                assertEquals(floor, resultSet.getString("floor"), slotId + " floor");
                assertEquals(bayNumber, resultSet.getInt("bay_number"), slotId + " bay number");
                assertEquals(zone, resultSet.getString("zone"), slotId + " zone");
                assertEquals(charging, resultSet.getInt("charging") == 1,
                        slotId + " charging flag");
                assertFalse(resultSet.next(), "Duplicate slot id " + slotId);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read slot " + slotId, e);
        }
    }
}

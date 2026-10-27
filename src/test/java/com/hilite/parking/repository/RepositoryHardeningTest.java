package com.hilite.parking.repository;

import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit D6 (friendly schema message before index creation) and R5 (corrupt stored rows surface
 * as {@link DataAccessException}, never as a raw parse or enum exception).
 */
class RepositoryHardeningTest {

    private static final String GARBAGE_TIMESTAMP = "not-a-timestamp-but-30-chars!!";

    @TempDir
    Path tempDir;

    @Test
    void malformedExistingTableGetsFriendlyMessageAndFullRollback() throws SQLException {
        Database database = new Database(RepositoryTestSupport.databaseFile(tempDir));
        // An old parking_sessions table without exit_time / final_fee_paise. Before the fix the
        // partial index "... WHERE exit_time IS NULL" failed first with "no such column".
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE parking_sessions (
                        session_id TEXT PRIMARY KEY, registration TEXT, vehicle_type TEXT,
                        charging_requested INTEGER, slot_id TEXT, entry_time TEXT)
                    """);
        }

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> new DatabaseInitializer(database).initialize());

        assertTrue(failure.getMessage().contains(
                        "table 'parking_sessions' is missing required column(s) exit_time, final_fee_paise"),
                failure.getMessage());
        assertEquals(List.of("parking_sessions"), RepositoryTestSupport.stringColumn(database,
                        "SELECT name FROM sqlite_master WHERE type IN ('table', 'index')"
                                + " AND name NOT LIKE 'sqlite_%' ORDER BY name"),
                "Tables created earlier in the transaction must be rolled back; no index created");
        assertEquals("delete", RepositoryTestSupport.scalarString(database, "PRAGMA journal_mode"),
                "A rejected file must not be switched to WAL");
    }

    @Test
    void corruptSessionTimestampIsWrappedInDataAccessException() {
        Database database = RepositoryTestSupport.initializedDatabase(tempDir);
        RepositoryTestSupport.executeSql(database, "INSERT INTO parking_sessions (session_id,"
                + " registration, vehicle_type, charging_requested, slot_id, entry_time)"
                + " VALUES ('S-BAD', 'KA01AB1234', 'CAR', 0, 'G-01', ?)", GARBAGE_TIMESTAMP);
        JdbcParkingSessionRepository sessions = new JdbcParkingSessionRepository(database);

        DataAccessException byId = assertThrows(DataAccessException.class,
                () -> sessions.findById("S-BAD"));
        assertInstanceOf(DateTimeParseException.class, byId.getCause());
        assertTrue(byId.getMessage().contains("S-BAD"), byId.getMessage());
        assertThrows(DataAccessException.class, sessions::findAll);
        assertThrows(DataAccessException.class, sessions::findActive);
    }

    @Test
    void corruptPaymentTimestampIsWrappedInDataAccessException() {
        Database database = RepositoryTestSupport.initializedDatabase(tempDir);
        RepositoryTestSupport.executeSql(database, "INSERT INTO parking_sessions (session_id,"
                        + " registration, vehicle_type, charging_requested, slot_id, entry_time)"
                        + " VALUES ('S-1', 'KA01AB1234', 'CAR', 0, 'G-01', ?)",
                RepositoryTestSupport.storedTimestamp(RepositoryTestSupport.ENTRY_TIME));
        RepositoryTestSupport.executeSql(database, "INSERT INTO payments (payment_id, session_id,"
                + " amount_paise, status, confirmed_at) VALUES ('P-BAD', 'S-1', 500,"
                + " 'CONFIRMED', ?)", GARBAGE_TIMESTAMP);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> new JdbcPaymentRepository(database).findAll());
        assertInstanceOf(DateTimeParseException.class, failure.getCause());
    }

    @Test
    void corruptSlotEnumIsWrappedInDataAccessException() throws SQLException {
        Database database = RepositoryTestSupport.initializedDatabase(tempDir);
        // CHECK constraints normally forbid this; switch them off on this one raw connection.
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA ignore_check_constraints = ON");
            statement.executeUpdate("UPDATE parking_slots SET zone = 'Z' WHERE slot_id = 'G-02'");
        }

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> new JdbcParkingSlotRepository(database).findAll());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("G-02"), failure.getMessage());
    }
}

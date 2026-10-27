package com.hilite.parking.repository;

import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAL journal mode and IMMEDIATE write transactions (audit R1/R2).
 *
 * <p>The waiting tests hold the write lock on a second connection with {@code BEGIN IMMEDIATE},
 * start the operation under test on another thread, prove it is still waiting (it has not
 * failed with {@code SQLITE_BUSY}), then release the lock and prove it completes. The pause is
 * well inside the five-second busy timeout.</p>
 */
class DatabaseConcurrencyTest {

    private static final Instant ENTRY = RepositoryTestSupport.ENTRY_TIME;

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() {
        database = RepositoryTestSupport.initializedDatabase(tempDir);
    }

    @Test
    void walJournalModeIsActiveAfterInitialize() {
        assertEquals("wal", RepositoryTestSupport.scalarString(database, "PRAGMA journal_mode"));
    }

    @Test
    void walModeIsStoredInTheFileAndSurvivesReopenAndReinitialize() {
        new DatabaseInitializer(database).initialize();
        Database reopened = new Database(database.file());
        assertEquals("wal", RepositoryTestSupport.scalarString(reopened, "PRAGMA journal_mode"));
    }

    @Test
    void busyTimeoutIsSetOnEveryConnection() {
        assertEquals(5000L, RepositoryTestSupport.scalarLong(database, "PRAGMA busy_timeout"));
    }

    @Test
    void readersAreNotBlockedByAnOpenWriteTransaction() throws Exception {
        new JdbcParkingSessionRepository(database).insertActive(
                RepositoryTestSupport.activeCar("S-1", "KA01AB0001", "G-01", ENTRY));
        try (Connection writer = database.openConnection()) {
            Database.beginImmediate(writer);
            execute(writer, "DELETE FROM parking_sessions");

            // WAL: the reader sees the last committed state immediately, without waiting.
            long started = System.nanoTime();
            assertEquals(1, new JdbcParkingSessionRepository(database).countEntries());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2000);

            Database.rollback(writer, new AssertionError("unused"));
        }
    }

    @Test
    void resetWaitsForAConcurrentWriterInsteadOfFailing() throws Exception {
        CompletableFuture<Void> reset;
        try (Connection writer = database.openConnection()) {
            Database.beginImmediate(writer);
            execute(writer, "INSERT INTO parking_sessions (session_id, registration, vehicle_type,"
                    + " charging_requested, slot_id, entry_time) VALUES ('S-W', 'KA01AB0009',"
                    + " 'CAR', 0, 'G-04', '" + RepositoryTestSupport.storedTimestamp(ENTRY) + "')");

            reset = CompletableFuture.runAsync(
                    () -> new DatabaseInitializer(database).resetDemoData());
            pause();
            assertFalse(reset.isDone(), "Reset must wait for the write lock, not fail with BUSY");

            Database.commit(writer);
        }

        reset.get(10, TimeUnit.SECONDS);
        // The reset ran after the writer committed, so it also removed the writer's session.
        assertEquals(0, new JdbcParkingSessionRepository(database).countEntries());
    }

    @Test
    void exitCompletionWaitsForAConcurrentWriterInsteadOfFailing() throws Exception {
        new JdbcParkingSessionRepository(database).insertActive(
                RepositoryTestSupport.activeCar("S-1", "KA01AB0001", "G-01", ENTRY));
        Instant exit = ENTRY.plus(Duration.ofMinutes(30));

        CompletableFuture<Boolean> completion;
        try (Connection writer = database.openConnection()) {
            Database.beginImmediate(writer);
            execute(writer, "UPDATE admin_settings SET setting_value = 'EXIT'"
                    + " WHERE setting_key = 'kiosk.mode'");

            completion = CompletableFuture.supplyAsync(() -> new JdbcExitCompletionRepository(
                    database).complete("S-1", exit, BigDecimal.ZERO, null));
            pause();
            assertFalse(completion.isDone(), "Exit must wait for the write lock");

            Database.commit(writer);
        }

        assertTrue(completion.get(10, TimeUnit.SECONDS));
        assertEquals(1, new JdbcParkingSessionRepository(database).countCompletedExits());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void pause() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(400);
    }
}

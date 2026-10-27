package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.ExitCompletionRepository;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.PaymentRepository;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Shared setup and raw-SQL helpers for the Phase 5 exit-service tests.
 *
 * <p>Every test owns a fresh {@code @TempDir}; this helper only builds the database inside that
 * directory, wires the real JDBC repositories and the real {@link FeeCalculator} to an
 * {@link ExitService}, and injects a fixed clock. Active sessions are always seeded through
 * {@link ParkingSessionRepository#insertActive}, so the tests exercise the same definition of an
 * open stay that the application uses. Raw statements exist only for the triggers that force a
 * transaction failure and for reading stored columns directly.</p>
 */
final class ExitTestSupport {

    /** Fixed instant used by the injected clock and by every seeded entry time. */
    static final Instant NOW = Instant.parse("2026-10-26T10:15:30Z");

    /** Clock pinned to {@link #NOW}; quote timestamps are deterministic. */
    static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ExitTestSupport() {
    }

    /** Builds an initialized database in {@code tempDir} and a fixture using {@link #FIXED_CLOCK}. */
    static Fixture fixture(Path tempDir) {
        return fixture(tempDir, FIXED_CLOCK);
    }

    /** Builds an initialized database in {@code tempDir} and a fixture using {@code clock}. */
    static Fixture fixture(Path tempDir, Clock clock) {
        Database database = new Database(tempDir.resolve("test.db"));
        new DatabaseInitializer(database).initialize();
        return new Fixture(database, clock);
    }

    /**
     * One temporary database with its real repositories and one exit service.
     *
     * <p>Seeded entry times are derived from {@link #NOW}, never from the wall clock, so every
     * expected duration and fee is deterministic.</p>
     */
    static final class Fixture {

        final Database database;
        final ParkingSlotRepository slots;
        final ParkingSessionRepository sessions;
        final PaymentRepository payments;
        final ExitCompletionRepository completions;
        final ExitService service;

        private Fixture(Database database, Clock clock) {
            this.database = database;
            this.slots = new JdbcParkingSlotRepository(database);
            this.sessions = new JdbcParkingSessionRepository(database);
            this.payments = new JdbcPaymentRepository(database);
            this.completions = new JdbcExitCompletionRepository(database);
            this.service = new ExitService(
                    slots, sessions, completions, new FeeCalculator(), clock);
        }

        /** Seeds an active stay that entered {@code stay} before {@link #NOW}. */
        ParkingSession seedActive(String sessionId, String registration,
                                  VehicleType vehicleType, String slotId, Duration stay) {
            return seedActiveAt(sessionId, registration, vehicleType, slotId, NOW.minus(stay));
        }

        /** Seeds an active stay at an explicit entry instant. */
        ParkingSession seedActiveAt(String sessionId, String registration,
                                    VehicleType vehicleType, String slotId,
                                    Instant entryTime) {
            ParkingSession session = new ParkingSession(
                    sessionId, registration, vehicleType, false, slotId, entryTime, null, null);
            sessions.insertActive(session);
            return session;
        }

        /** Number of session rows of any state. */
        long sessionCount() {
            return sessions.countEntries();
        }

        /** Number of completed session rows. */
        long completedSessionCount() {
            return sessions.countCompletedExits();
        }

        /** Number of payment rows of any status, read directly from the table. */
        long paymentRowCount() {
            return number("SELECT COUNT(*) FROM payments");
        }

        /** Reads one stored session column as text; {@code null} stays {@code null}. */
        String storedSessionText(String column, String sessionId) {
            return text("SELECT " + column + " FROM parking_sessions WHERE session_id = ?",
                    sessionId);
        }

        /** Executes one raw statement, used to install a test-only trigger. */
        void execute(String sql) {
            try (Connection connection = database.openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (SQLException e) {
                throw new AssertionError("raw statement failed: " + sql, e);
            }
        }

        /** Runs a raw scalar query that returns a number. */
        long number(String sql, Object... parameters) {
            try (Connection connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new AssertionError("raw query returned no row: " + sql);
                    }
                    return resultSet.getLong(1);
                }
            } catch (SQLException e) {
                throw new AssertionError("raw query failed: " + sql, e);
            }
        }

        /** Runs a raw scalar query that returns text. */
        String text(String sql, Object... parameters) {
            try (Connection connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new AssertionError("raw query returned no row: " + sql);
                    }
                    return resultSet.getString(1);
                }
            } catch (SQLException e) {
                throw new AssertionError("raw query failed: " + sql, e);
            }
        }

        private static void bind(PreparedStatement statement, Object... parameters)
                throws SQLException {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
        }
    }
}

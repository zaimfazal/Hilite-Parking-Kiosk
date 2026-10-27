package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.SettingsRepository;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Shared setup for the Phase 6 admin-service tests (access, statistics, long stay).
 *
 * <p>Every test owns a fresh {@code @TempDir}; the database is created there by the real
 * {@link DatabaseInitializer}. Active sessions are seeded through
 * {@link ParkingSessionRepository#insertActive}; completed sessions are seeded as an active
 * insert followed by {@link ParkingSessionRepository#updateCompleted}, which is the same
 * persistence path the application uses. Raw SQL exists only to corrupt settings and to count
 * rows directly.</p>
 */
final class AdminTestSupport {

    /** Fixed "now" for every clock injected by these tests. */
    static final Instant NOW = Instant.parse("2026-10-26T10:15:30Z");

    /** Clock pinned to {@link #NOW}. */
    static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private AdminTestSupport() {
    }

    /** Builds an initialized database in {@code tempDir} with its real repositories. */
    static Fixture fixture(Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        new DatabaseInitializer(database).initialize();
        return new Fixture(database);
    }

    /** One temporary database and the real JDBC repositories over it. */
    static final class Fixture {

        final Database database;
        final ParkingSlotRepository slots;
        final ParkingSessionRepository sessions;
        final SettingsRepository settings;

        private Fixture(Database database) {
            this.database = database;
            this.slots = new JdbcParkingSlotRepository(database);
            this.sessions = new JdbcParkingSessionRepository(database);
            this.settings = new JdbcSettingsRepository(database);
        }

        /** A real exit service over this database, using {@code clock}. */
        ExitService exitService(Clock clock) {
            return new ExitService(slots, sessions, new JdbcExitCompletionRepository(database),
                    new FeeCalculator(), clock);
        }

        /** Seeds an active session without a charging request. */
        ParkingSession active(String sessionId, String registration, VehicleType type,
                              String slotId, Instant entryTime) {
            return active(sessionId, registration, type, slotId, entryTime, false);
        }

        /** Seeds an active session. */
        ParkingSession active(String sessionId, String registration, VehicleType type,
                              String slotId, Instant entryTime, boolean charging) {
            ParkingSession session = new ParkingSession(
                    sessionId, registration, type, charging, slotId, entryTime, null, null);
            sessions.insertActive(session);
            return session;
        }

        /** Seeds a completed session with a zero fee, which frees the bay again. */
        ParkingSession completed(String sessionId, String registration, VehicleType type,
                                 String slotId, Instant entryTime, Instant exitTime) {
            active(sessionId, registration, type, slotId, entryTime);
            ParkingSession completed = new ParkingSession(sessionId, registration, type, false,
                    slotId, entryTime, exitTime, BigDecimal.ZERO);
            sessions.updateCompleted(completed);
            return completed;
        }

        /** Deletes one settings row directly, to simulate an absent value. */
        void deleteSetting(String key) {
            update("DELETE FROM admin_settings WHERE setting_key = ?", key);
        }

        /** Runs a raw update statement. */
        void update(String sql, Object... parameters) {
            try (Connection connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                statement.executeUpdate();
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

        private static void bind(PreparedStatement statement, Object... parameters)
                throws SQLException {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
        }
    }
}

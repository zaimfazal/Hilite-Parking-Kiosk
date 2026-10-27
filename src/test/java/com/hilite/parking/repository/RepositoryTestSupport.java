package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.model.VehicleType;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared setup and raw-SQL helpers for the Phase 3 persistence tests.
 *
 * <p>Every test owns a fresh {@code @TempDir}; this helper only builds the database inside that
 * directory. Normal CRUD always goes through a repository. The raw statements here exist for the
 * tests that deliberately prove database constraints or inspect the stored representation.</p>
 */
final class RepositoryTestSupport {

    /** Fixed entry instant: 2026-10-25 08:00:00 UTC. */
    static final Instant ENTRY_TIME = Instant.parse("2026-10-25T08:00:00Z");

    /** The frozen storage format: ISO-8601 UTC text with exactly nine fractional digits. */
    static final DateTimeFormatter STORED_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private RepositoryTestSupport() {
    }

    static Path databaseFile(Path tempDir) {
        return tempDir.resolve("test.db");
    }

    static Database initializedDatabase(Path tempDir) {
        Database database = new Database(databaseFile(tempDir));
        new DatabaseInitializer(database).initialize();
        return database;
    }

    static ParkingSession activeCar(String sessionId, String registration,
                                    String slotId, Instant entryTime) {
        return new ParkingSession(sessionId, registration, VehicleType.CAR, false,
                slotId, entryTime, null, null);
    }

    static ParkingSession completedCar(String sessionId, String registration, String slotId,
                                       Instant entryTime, Instant exitTime, String finalFee) {
        return new ParkingSession(sessionId, registration, VehicleType.CAR, false,
                slotId, entryTime, exitTime, new BigDecimal(finalFee));
    }

    static Payment pendingPayment(String paymentId, String sessionId, String amount) {
        return new Payment(paymentId, sessionId, new BigDecimal(amount),
                PaymentStatus.PENDING, null);
    }

    static Payment confirmedPayment(String paymentId, String sessionId,
                                    String amount, Instant confirmedAt) {
        return new Payment(paymentId, sessionId, new BigDecimal(amount),
                PaymentStatus.CONFIRMED, confirmedAt);
    }

    static String storedTimestamp(Instant instant) {
        return STORED_TIMESTAMP.format(instant);
    }

    static List<String> sessionIds(List<ParkingSession> sessions) {
        List<String> ids = new ArrayList<>();
        for (ParkingSession session : sessions) {
            ids.add(session.sessionId());
        }
        return ids;
    }

    static List<String> paymentIds(List<Payment> payments) {
        List<String> ids = new ArrayList<>();
        for (Payment payment : payments) {
            ids.add(payment.paymentId());
        }
        return ids;
    }

    static long scalarLong(Database database, String sql, Object... parameters) {
        try (Connection connection = database.openConnection()) {
            return scalarLong(connection, sql, parameters);
        } catch (SQLException e) {
            throw new AssertionError("raw query failed: " + sql, e);
        }
    }

    static long scalarLong(Connection connection, String sql, Object... parameters) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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

    static String scalarString(Database database, String sql, Object... parameters) {
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

    static List<String> stringColumn(Database database, String sql, Object... parameters) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        } catch (SQLException e) {
            throw new AssertionError("raw query failed: " + sql, e);
        }
    }

    static void executeSql(Database database, String sql, Object... parameters) {
        try (Connection connection = database.openConnection()) {
            if (parameters.length == 0) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bind(statement, parameters);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new AssertionError("raw statement failed: " + sql, e);
        }
    }

    /**
     * Inserts a session with raw SQL so a test can prove a database CHECK constraint.
     * The caller asserts the resulting {@link SQLException}.
     */
    static void insertRawSession(Database database, String sessionId, String registration,
                                 String slotId, Instant entryTime, Instant exitTime,
                                 Long finalFeePaise) throws SQLException {
        String sql = """
                INSERT INTO parking_sessions
                    (session_id, registration, vehicle_type, charging_requested,
                     slot_id, entry_time, exit_time, final_fee_paise)
                VALUES (?, ?, 'CAR', 0, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            statement.setString(2, registration);
            statement.setString(3, slotId);
            statement.setString(4, storedTimestamp(entryTime));
            if (exitTime == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, storedTimestamp(exitTime));
            }
            if (finalFeePaise == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setLong(6, finalFeePaise);
            }
            statement.executeUpdate();
        }
    }

    /**
     * Inserts a payment with raw SQL so a test can prove the status/timestamp CHECK constraint.
     * The caller asserts the resulting {@link SQLException}.
     */
    static void insertRawPayment(Database database, String paymentId, String sessionId,
                                 long amountPaise, String status, Instant confirmedAt)
            throws SQLException {
        String sql = """
                INSERT INTO payments (payment_id, session_id, amount_paise, status, confirmed_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, paymentId);
            statement.setString(2, sessionId);
            statement.setLong(3, amountPaise);
            statement.setString(4, status);
            if (confirmedAt == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, storedTimestamp(confirmedAt));
            }
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters)
            throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}

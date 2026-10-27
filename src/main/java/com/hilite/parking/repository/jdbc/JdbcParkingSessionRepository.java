package com.hilite.parking.repository.jdbc;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.util.RegistrationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link ParkingSessionRepository}.
 *
 * <p>A session is active while {@code exit_time} is null, and completed once both
 * {@code exit_time} and {@code final_fee_paise} are set. Completion is a conditional update
 * ({@code WHERE exit_time IS NULL}), so the same session can never be completed or charged
 * twice. Each call uses its own connection; no transaction is held across calls.</p>
 */
public final class JdbcParkingSessionRepository implements ParkingSessionRepository {

    private static final String SELECT_COLUMNS =
            "SELECT session_id, registration, vehicle_type, charging_requested, slot_id, "
                    + "entry_time, exit_time, final_fee_paise FROM parking_sessions";

    private static final String ORDER_BY = " ORDER BY entry_time, session_id";

    private final Database database;

    /** Creates the repository. */
    public JdbcParkingSessionRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public void insertActive(ParkingSession session) {
        Objects.requireNonNull(session, "session is required");
        if (session.exitTime() != null || session.finalFee() != null) {
            throw new IllegalArgumentException("Only an active session can be inserted");
        }
        if (!session.registration().equals(RegistrationUtil.normalize(session.registration()))) {
            throw new IllegalArgumentException(
                    "registration must be normalized with RegistrationUtil.normalize");
        }

        String sql = "INSERT INTO parking_sessions (session_id, registration, vehicle_type, "
                + "charging_requested, slot_id, entry_time, exit_time, final_fee_paise) "
                + "VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.sessionId());
            statement.setString(2, session.registration());
            statement.setString(3, session.vehicleType().name());
            statement.setInt(4, session.chargingRequested() ? 1 : 0);
            statement.setString(5, session.slotId());
            statement.setString(6, SqlCodec.toText(session.entryTime()));
            int inserted = statement.executeUpdate();
            if (inserted != 1) {
                throw new DataAccessException(
                        "Parking session " + session.sessionId() + " was not inserted");
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Inserting parking session", e);
        }
    }

    @Override
    public Optional<ParkingSession> findActiveByRegistration(String normalizedRegistration) {
        Objects.requireNonNull(normalizedRegistration, "normalizedRegistration is required");
        String sql = SELECT_COLUMNS + " WHERE registration = ? AND exit_time IS NULL";
        return queryOne(sql, normalizedRegistration, "Loading active parking session");
    }

    @Override
    public Optional<ParkingSession> findLatestByRegistration(String normalizedRegistration) {
        Objects.requireNonNull(normalizedRegistration, "normalizedRegistration is required");
        String sql = SELECT_COLUMNS + " WHERE registration = ?"
                + " ORDER BY entry_time DESC, session_id DESC LIMIT 1";
        return queryOne(sql, normalizedRegistration, "Loading latest parking session");
    }

    @Override
    public Optional<ParkingSession> findById(String sessionId) {        Objects.requireNonNull(sessionId, "sessionId is required");
        String sql = SELECT_COLUMNS + " WHERE session_id = ?";
        return queryOne(sql, sessionId, "Loading parking session");
    }

    @Override
    public List<ParkingSession> findActive() {
        return querySessions(SELECT_COLUMNS + " WHERE exit_time IS NULL" + ORDER_BY,
                "Loading active parking sessions");
    }

    @Override
    public List<ParkingSession> findAll() {
        return querySessions(SELECT_COLUMNS + ORDER_BY, "Loading parking sessions");
    }

    @Override
    public void updateCompleted(ParkingSession completedSession) {
        Objects.requireNonNull(completedSession, "completedSession is required");
        if (completedSession.exitTime() == null || completedSession.finalFee() == null) {
            throw new IllegalArgumentException("A completed session needs exitTime and finalFee");
        }

        String sql = "UPDATE parking_sessions SET exit_time = ?, final_fee_paise = ? "
                + "WHERE session_id = ? AND exit_time IS NULL";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SqlCodec.toText(completedSession.exitTime()));
            statement.setLong(2, SqlCodec.toPaise(completedSession.finalFee()));
            statement.setString(3, completedSession.sessionId());
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new DataAccessException(
                        "No active session found for id " + completedSession.sessionId());
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Completing parking session", e);
        }
    }

    @Override
    public long countEntries() {
        return count("SELECT COUNT(*) FROM parking_sessions", "Counting parking sessions");
    }

    @Override
    public long countCompletedExits() {
        return count("SELECT COUNT(*) FROM parking_sessions WHERE exit_time IS NOT NULL",
                "Counting completed parking sessions");
    }

    @Override
    public Optional<ParkingSession> findActiveBySlot(String slotId) {
        Objects.requireNonNull(slotId, "slotId is required");
        String sql = SELECT_COLUMNS + " WHERE exit_time IS NULL AND slot_id = ?";
        return queryOne(sql, slotId, "Loading active parking session for slot");
    }

    private Optional<ParkingSession> queryOne(String sql, String parameter, String action) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapRow(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlCodec.failure(action, e);
        }
    }

    private List<ParkingSession> querySessions(String sql, String action) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<ParkingSession> sessions = new ArrayList<>();
            while (results.next()) {
                sessions.add(mapRow(results));
            }
            return List.copyOf(sessions);
        } catch (SQLException e) {
            throw SqlCodec.failure(action, e);
        }
    }

    private long count(String sql, String action) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return results.next() ? results.getLong(1) : 0L;
        } catch (SQLException e) {
            throw SqlCodec.failure(action, e);
        }
    }

    private static ParkingSession mapRow(ResultSet results) throws SQLException {
        String sessionId = results.getString("session_id");
        String registration = results.getString("registration");
        String vehicleType = results.getString("vehicle_type");
        int chargingRequested = results.getInt("charging_requested");
        String slotId = results.getString("slot_id");
        String entryText = results.getString("entry_time");
        String exitText = results.getString("exit_time");
        long feePaise = results.getLong("final_fee_paise");
        boolean feeIsNull = results.wasNull();

        try {
            return new ParkingSession(
                    sessionId,
                    registration,
                    VehicleType.valueOf(vehicleType),
                    chargingRequested == 1,
                    slotId,
                    SqlCodec.parseInstant(entryText),
                    exitText == null ? null : SqlCodec.parseInstant(exitText),
                    feeIsNull ? null : SqlCodec.fromPaise(feePaise));
        } catch (DateTimeException | IllegalArgumentException | NullPointerException corrupt) {
            throw SqlCodec.corruptRow("parking_sessions", sessionId, corrupt);
        }
    }
}

package com.hilite.parking.repository.jdbc;

import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.PaymentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC implementation of {@link PaymentRepository}.
 *
 * <p>Amounts are persisted as integer paise through {@link SqlCodec#toPaise}. The model
 * already guarantees that a confirmed payment has a timestamp and a pending one does not;
 * the database repeats that rule as a check constraint.</p>
 */
public final class JdbcPaymentRepository implements PaymentRepository {

    private static final String SELECT_COLUMNS =
            "SELECT payment_id, session_id, amount_paise, status, confirmed_at FROM payments";

    private final Database database;

    /** Creates the repository. */
    public JdbcPaymentRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public void insert(Payment payment) {
        Objects.requireNonNull(payment, "payment is required");
        String sql = "INSERT INTO payments (payment_id, session_id, amount_paise, status, "
                + "confirmed_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payment.paymentId());
            statement.setString(2, payment.sessionId());
            statement.setLong(3, SqlCodec.toPaise(payment.amount()));
            statement.setString(4, payment.status().name());
            statement.setString(5, payment.confirmedAt() == null
                    ? null
                    : SqlCodec.toText(payment.confirmedAt()));
            int inserted = statement.executeUpdate();
            if (inserted != 1) {
                throw new DataAccessException(
                        "Payment " + payment.paymentId() + " was not inserted");
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Inserting payment", e);
        }
    }

    @Override
    public List<Payment> findBySessionId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        String sql = SELECT_COLUMNS + " WHERE session_id = ? ORDER BY payment_id";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet results = statement.executeQuery()) {
                return mapPayments(results);
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Loading payments for session", e);
        }
    }

    @Override
    public List<Payment> findAll() {
        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_COLUMNS + " ORDER BY payment_id");
             ResultSet results = statement.executeQuery()) {
            return mapPayments(results);
        } catch (SQLException e) {
            throw SqlCodec.failure("Loading payments", e);
        }
    }

    @Override
    public long countConfirmed() {
        String sql = "SELECT COUNT(*) FROM payments WHERE status = 'CONFIRMED'";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return results.next() ? results.getLong(1) : 0L;
        } catch (SQLException e) {
            throw SqlCodec.failure("Counting confirmed payments", e);
        }
    }

    private static List<Payment> mapPayments(ResultSet results) throws SQLException {
        List<Payment> payments = new ArrayList<>();
        while (results.next()) {
            payments.add(mapRow(results));
        }
        return List.copyOf(payments);
    }

    private static Payment mapRow(ResultSet results) throws SQLException {
        String paymentId = results.getString("payment_id");
        String sessionId = results.getString("session_id");
        long amountPaise = results.getLong("amount_paise");
        String status = results.getString("status");
        String confirmedText = results.getString("confirmed_at");

        try {
            Instant confirmedAt =
                    confirmedText == null ? null : SqlCodec.parseInstant(confirmedText);
            return new Payment(
                    paymentId,
                    sessionId,
                    SqlCodec.fromPaise(amountPaise),
                    PaymentStatus.valueOf(status),
                    confirmedAt);
        } catch (DateTimeException | IllegalArgumentException | NullPointerException corrupt) {
            throw SqlCodec.corruptRow("payments", paymentId, corrupt);
        }
    }
}

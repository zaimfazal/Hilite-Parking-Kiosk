package com.hilite.parking.repository.jdbc;

import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ExitCompletionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

/**
 * JDBC implementation of {@link ExitCompletionRepository}.
 *
 * <p><strong>One connection, one transaction.</strong> Closing the session and inserting the
 * confirmed payment both run on the same {@link Connection} with auto-commit disabled. The
 * method returns {@code true} only after {@link Connection#commit()} succeeds.</p>
 *
 * <p><strong>Conditional update is the concurrency protection.</strong> The session is closed
 * with {@code WHERE session_id = ? AND exit_time IS NULL}. SQLite applies that update
 * atomically, so if two exit attempts race, only one can affect a row. The loser sees an update
 * count of zero and receives {@code false}; no payment is inserted for it.</p>
 *
 * <p><strong>All or nothing.</strong> The session update runs first, so a zero-fee exit and a
 * paid exit share one code path. When a payment is supplied it is inserted on the same
 * connection immediately afterwards. If the payment insert fails, the session update is rolled
 * back as well: either both rows are committed or neither is.</p>
 *
 * <p>Any SQL failure rolls the transaction back and is rethrown as
 * {@link DataAccessException} with the original cause preserved. Any runtime failure rolls the
 * transaction back and is rethrown unchanged. A rollback failure is attached to the primary
 * failure as a suppressed exception.</p>
 */
public final class JdbcExitCompletionRepository implements ExitCompletionRepository {

    private static final String COMPLETE_SESSION_SQL =
            "UPDATE parking_sessions SET exit_time = ?, final_fee_paise = ? "
                    + "WHERE session_id = ? AND exit_time IS NULL";

    private static final String INSERT_PAYMENT_SQL =
            "INSERT INTO payments (payment_id, session_id, amount_paise, status, confirmed_at) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private final Database database;

    /**
     * Creates the repository.
     *
     * @throws NullPointerException if {@code database} is {@code null}
     */
    public JdbcExitCompletionRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database is required");
    }

    @Override
    public boolean complete(String sessionId, Instant exitTime, BigDecimal finalFee,
                            Payment payment) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(exitTime, "exitTime is required");
        Objects.requireNonNull(finalFee, "finalFee is required");
        if (finalFee.signum() < 0) {
            throw new IllegalArgumentException(
                    "finalFee must not be negative: " + finalFee.toPlainString());
        }
        if (payment != null) {
            validatePayment(sessionId, finalFee, payment);
        }

        try (Connection connection = database.openConnection()) {
            try {
                connection.setAutoCommit(false);

                if (!completeSession(connection, sessionId, exitTime, finalFee)) {
                    connection.rollback();
                    return false;
                }

                if (payment != null) {
                    insertPayment(connection, payment);
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                rollback(connection, e);
                throw SqlCodec.failure("Completing exit", e);
            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw SqlCodec.failure("Completing exit", e);
        }
    }

    private static void validatePayment(String sessionId, BigDecimal finalFee, Payment payment) {
        if (payment.status() != PaymentStatus.CONFIRMED) {
            throw new IllegalArgumentException("payment must be CONFIRMED");
        }
        if (!payment.sessionId().equals(sessionId)) {
            throw new IllegalArgumentException(
                    "payment sessionId must match sessionId: " + payment.sessionId()
                            + " != " + sessionId);
        }
        if (payment.amount().compareTo(finalFee) != 0) {
            throw new IllegalArgumentException(
                    "payment amount must equal finalFee: " + payment.amount().toPlainString()
                            + " != " + finalFee.toPlainString());
        }
    }

    /**
     * Closes the session only while it is still active and reports whether this call won.
     *
     * @return {@code true} when exactly one row was updated
     */
    private static boolean completeSession(Connection connection, String sessionId,
                                           Instant exitTime, BigDecimal finalFee)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COMPLETE_SESSION_SQL)) {
            statement.setString(1, SqlCodec.toText(exitTime));
            statement.setLong(2, SqlCodec.toPaise(finalFee));
            statement.setString(3, sessionId);

            int updated = statement.executeUpdate();
            if (updated == 0) {
                return false;
            }
            if (updated != 1) {
                throw new DataAccessException(
                        "Exit completion updated " + updated + " sessions for id " + sessionId);
            }
            return true;
        }
    }

    private static void insertPayment(Connection connection, Payment payment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PAYMENT_SQL)) {
            statement.setString(1, payment.paymentId());
            statement.setString(2, payment.sessionId());
            statement.setLong(3, SqlCodec.toPaise(payment.amount()));
            statement.setString(4, payment.status().name());
            statement.setString(5, SqlCodec.toText(payment.confirmedAt()));

            int inserted = statement.executeUpdate();
            if (inserted != 1) {
                throw new DataAccessException(
                        "Payment " + payment.paymentId() + " was not inserted");
            }
        }
    }

    /**
     * Rolls back, attaching a rollback failure to the primary failure instead of replacing it.
     */
    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}

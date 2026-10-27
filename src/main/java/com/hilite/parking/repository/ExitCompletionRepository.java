package com.hilite.parking.repository;

import com.hilite.parking.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One atomic exit-completion boundary.
 *
 * <p>{@link com.hilite.parking.repository.ParkingSessionRepository#updateCompleted} and
 * {@link PaymentRepository#insert} each open their own connection, so calling them one after
 * another would leave a window where a paid session is closed but unpaid — or the reverse.
 * This repository exists to perform both writes on one SQLite connection inside one
 * transaction.</p>
 *
 * <p>The session update is conditional ({@code WHERE exit_time IS NULL}), so two exit attempts
 * can never both complete the same session. The caller only reports success after
 * {@link #complete} returns {@code true}, which happens after the commit.</p>
 */
public interface ExitCompletionRepository {

    /**
     * Completes one active session and optionally records its confirmed demo payment.
     *
     * <p>Both writes happen in a single transaction. If either fails, everything is rolled
     * back: the session stays active and no payment row survives.</p>
     *
     * @param sessionId identifier of the session to complete
     * @param exitTime  quoted exit instant, stored unchanged
     * @param finalFee  final fee in rupees; must not be negative
     * @param payment   confirmed payment to insert, or {@code null} for a zero-fee exit
     * @return {@code true} when this call completed the session and committed; {@code false}
     *         when the session was no longer active (already completed elsewhere), in which
     *         case nothing was written
     * @throws IllegalArgumentException if a supplied payment is not confirmed, belongs to
     *                                  another session, or its amount differs from
     *                                  {@code finalFee}
     * @throws DataAccessException      if the database operation fails; the transaction is
     *                                  rolled back before this exception is thrown
     */
    boolean complete(String sessionId, Instant exitTime, BigDecimal finalFee, Payment payment);
}

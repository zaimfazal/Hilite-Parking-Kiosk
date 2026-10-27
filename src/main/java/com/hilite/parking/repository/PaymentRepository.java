package com.hilite.parking.repository;

import com.hilite.parking.model.Payment;

import java.util.List;

/**
 * Persistence for demo payment records.
 *
 * <p>Amounts are stored as integer paise, never floating point. A payment always refers to an
 * existing parking session through a foreign key. No payment row is created during vehicle
 * entry; only the exit workflow (Phase 5) records one.</p>
 */
public interface PaymentRepository {

    /**
     * Inserts one payment.
     *
     * @throws IllegalArgumentException if the amount is not a whole number of paise (checked
     *                                  before any SQL runs)
     * @throws DataAccessException      if the session does not exist, the payment id is already
     *                                  used, or the status/timestamp rule is violated
     */
    void insert(Payment payment);

    /** Returns the payments for one session, ordered by payment id. */
    List<Payment> findBySessionId(String sessionId);

    /** Returns every payment, ordered by payment id. */
    List<Payment> findAll();

    /** Counts confirmed payments. Returns zero on an empty table. */
    long countConfirmed();
}

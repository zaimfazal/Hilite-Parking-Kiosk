package com.hilite.parking.repository;

import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects payment persistence: amounts are stored as integer paise and round-trip exactly,
 * payments in ascending id order, the status/timestamp pairing is a database CHECK, a session
 * foreign key must exist, and at most one CONFIRMED payment may exist per session.
 */
class PaymentRepositoryTest {

    private static final Instant ENTRY = RepositoryTestSupport.ENTRY_TIME;

    @TempDir
    Path tempDir;

    private Database database;
    private PaymentRepository payments;
    private ParkingSessionRepository sessions;

    @BeforeEach
    void setUp() {
        database = RepositoryTestSupport.initializedDatabase(tempDir);
        payments = new JdbcPaymentRepository(database);
        sessions = new JdbcParkingSessionRepository(database);
    }

    // ------------------------------------------------------------------
    // Required scenario 27: empty table
    // ------------------------------------------------------------------

    @Test
    void emptyTableHasNoPaymentsAndNoConfirmedCount() {
        assertEquals(0, payments.countConfirmed());
        assertTrue(payments.findAll().isEmpty());
        assertTrue(payments.findBySessionId("S-999").isEmpty());
    }

    // ------------------------------------------------------------------
    // Required scenario 28: ordering and confirmed count
    // ------------------------------------------------------------------

    @Test
    void insertedPaymentsAreReturnedInPaymentIdOrder() {
        insertSession("S-001", "KA01AB1234", "G-01", ENTRY);
        insertSession("S-002", "KA02CD5678", "G-02", ENTRY);

        payments.insert(RepositoryTestSupport.pendingPayment("PAY-003", "S-001", "5.00"));
        payments.insert(RepositoryTestSupport.pendingPayment("PAY-001", "S-001", "2.00"));
        payments.insert(RepositoryTestSupport.confirmedPayment(
                "PAY-002", "S-002", "5.00", ENTRY.plus(Duration.ofHours(3))));

        assertEquals(List.of("PAY-001", "PAY-003"),
                RepositoryTestSupport.paymentIds(payments.findBySessionId("S-001")),
                "Session payments must be ordered by payment id");
        assertEquals(List.of("PAY-001", "PAY-002", "PAY-003"),
                RepositoryTestSupport.paymentIds(payments.findAll()),
                "findAll must be ordered by payment id");
        assertEquals(1, payments.countConfirmed());
    }

    // ------------------------------------------------------------------
    // Required scenario 29: money round-trips as integer paise
    // ------------------------------------------------------------------

    @Test
    void moneyRoundTripsExactlyAsPaise() {
        String[] amounts = {"0", "2", "5", "15", "5.50"};
        long[] expectedPaise = {0L, 200L, 500L, 1500L, 550L};
        String[] registrations = {
                "KA01AB1234", "KA02CD5678", "KA03EF9012", "KA04GH3456", "KA05IJ7890"
        };

        for (int index = 0; index < amounts.length; index++) {
            String sessionId = String.format("S-%03d", index + 1);
            String slotId = String.format("G-%02d", index + 1);
            insertSession(sessionId, registrations[index], slotId,
                    ENTRY.plus(Duration.ofMinutes(index)));
            payments.insert(RepositoryTestSupport.pendingPayment(
                    String.format("PAY-%03d", index + 1), sessionId, amounts[index]));
        }

        for (int index = 0; index < amounts.length; index++) {
            String sessionId = String.format("S-%03d", index + 1);
            List<Payment> stored = payments.findBySessionId(sessionId);
            assertEquals(1, stored.size());
            assertEquals(0, new BigDecimal(amounts[index]).compareTo(stored.getFirst().amount()),
                    "Amount " + amounts[index] + " must round-trip exactly");
        }

        assertEquals(550L, RepositoryTestSupport.scalarLong(database,
                "SELECT amount_paise FROM payments WHERE payment_id = ?", "PAY-005"),
                "Rs 5.50 must be stored as 550 paise");
        assertEquals(0L, RepositoryTestSupport.scalarLong(database,
                "SELECT amount_paise FROM payments WHERE payment_id = ?", "PAY-001"));

        for (int index = 0; index < expectedPaise.length; index++) {
            assertEquals(expectedPaise[index], RepositoryTestSupport.scalarLong(database,
                    "SELECT amount_paise FROM payments WHERE payment_id = ?",
                    String.format("PAY-%03d", index + 1)));
        }
    }

    // ------------------------------------------------------------------
    // Required scenario 30: sub-paise amounts are rejected
    // ------------------------------------------------------------------

    @Test
    void subPaiseAmountIsRejected() {
        insertSession("S-001", "KA01AB1234", "G-01", ENTRY);

        assertThrows(IllegalArgumentException.class,
                () -> payments.insert(RepositoryTestSupport.pendingPayment(
                        "PAY-001", "S-001", "0.005")),
                "An amount with fractional paise cannot be stored");

        assertTrue(payments.findAll().isEmpty());
    }

    // ------------------------------------------------------------------
    // Required scenario 31: session foreign key
    // ------------------------------------------------------------------

    @Test
    void paymentForUnknownSessionIsRejected() {
        assertThrows(DataAccessException.class,
                () -> payments.insert(RepositoryTestSupport.pendingPayment(
                        "PAY-001", "S-999", "5.00")),
                "A payment must reference an existing session");

        assertTrue(payments.findAll().isEmpty());
    }

    // ------------------------------------------------------------------
    // Required scenario 32: status/timestamp CHECK constraints
    // ------------------------------------------------------------------

    @Test
    void databaseRejectsInconsistentConfirmationTimestamps() throws SQLException {
        insertSession("S-001", "KA01AB1234", "G-01", ENTRY);

        assertThrows(SQLException.class,
                () -> RepositoryTestSupport.insertRawPayment(
                        database, "PAY-001", "S-001", 500L, "CONFIRMED", null),
                "CONFIRMED without confirmed_at must violate a CHECK constraint");

        assertThrows(SQLException.class,
                () -> RepositoryTestSupport.insertRawPayment(
                        database, "PAY-002", "S-001", 500L, "PENDING",
                        ENTRY.plus(Duration.ofHours(3))),
                "PENDING with confirmed_at must violate a CHECK constraint");

        assertTrue(payments.findAll().isEmpty());
    }

    // ------------------------------------------------------------------
    // Required scenario 33: one confirmed payment per session
    // ------------------------------------------------------------------

    @Test
    void secondConfirmedPaymentForSameSessionIsRejected() {
        insertSession("S-001", "KA01AB1234", "G-01", ENTRY);
        Instant confirmedAt = ENTRY.plus(Duration.ofHours(3));

        payments.insert(RepositoryTestSupport.confirmedPayment(
                "PAY-001", "S-001", "5.00", confirmedAt));

        assertThrows(DataAccessException.class,
                () -> payments.insert(RepositoryTestSupport.confirmedPayment(
                        "PAY-002", "S-001", "5.00", confirmedAt.plusSeconds(1))),
                "A session may have only one confirmed payment");

        assertEquals(1, payments.countConfirmed());
        assertEquals(List.of("PAY-001"),
                RepositoryTestSupport.paymentIds(payments.findAll()));
    }

    // ------------------------------------------------------------------
    // Required scenario 34: payments survive a restart
    // ------------------------------------------------------------------

    @Test
    void paymentsSurviveDatabaseReopen() {
        insertSession("S-001", "KA01AB1234", "G-01", ENTRY);
        insertSession("S-002", "KA02CD5678", "G-02", ENTRY);

        Payment pending = RepositoryTestSupport.pendingPayment("PAY-001", "S-001", "5.00");
        Payment confirmed = RepositoryTestSupport.confirmedPayment(
                "PAY-002", "S-002", "5.50", ENTRY.plus(Duration.ofHours(3)));
        payments.insert(pending);
        payments.insert(confirmed);

        Database reopened = new Database(database.file());
        PaymentRepository afterRestart = new JdbcPaymentRepository(reopened);

        assertEquals(List.of("PAY-001", "PAY-002"),
                RepositoryTestSupport.paymentIds(afterRestart.findAll()));
        assertEquals(1, afterRestart.countConfirmed());

        Payment loadedPending = afterRestart.findBySessionId("S-001").getFirst();
        assertEquals(PaymentStatus.PENDING, loadedPending.status());
        assertNull(loadedPending.confirmedAt());
        assertEquals(0, pending.amount().compareTo(loadedPending.amount()));

        Payment loadedConfirmed = afterRestart.findBySessionId("S-002").getFirst();
        assertEquals(PaymentStatus.CONFIRMED, loadedConfirmed.status());
        assertEquals(confirmed.confirmedAt(), loadedConfirmed.confirmedAt());
        assertEquals(0, confirmed.amount().compareTo(loadedConfirmed.amount()));
    }

    private void insertSession(String sessionId, String registration,
                               String slotId, Instant entryTime) {
        sessions.insertActive(RepositoryTestSupport.activeCar(
                sessionId, registration, slotId, entryTime));
    }
}

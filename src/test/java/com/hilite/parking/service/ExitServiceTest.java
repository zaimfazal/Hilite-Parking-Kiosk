package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.PaymentRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the frozen exit/billing rules and the single-transaction guarantee against a real
 * temporary SQLite database.
 *
 * <p>Every test owns its own {@code @TempDir} and injects one fixed clock. Sessions are seeded
 * as active stays through the real repository, so quoting, payment confirmation, and zero-fee
 * completion all run against the same persistence path as the application. Fees are compared
 * with {@link BigDecimal#compareTo} because the database returns rupees with scale 2 while the
 * calculator returns scale 0.</p>
 *
 * <p>The trigger tests deliberately make one write fail and then prove the paired write was
 * rolled back: a completed session never exists without its confirmed payment, and a failed
 * completion never frees the bay.</p>
 */
class ExitServiceTest {

    private static final Instant NOW = ExitTestSupport.NOW;
    private static final Duration FOUR_HOURS_TWENTY_FOUR =
            Duration.ofHours(4).plusMinutes(24);

    @TempDir
    Path tempDir;

    private ExitTestSupport.Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = ExitTestSupport.fixture(tempDir);
    }

    // ------------------------------------------------------------------
    // Quote (scenarios 1-12)
    // ------------------------------------------------------------------

    @Test
    void carQuoteIsCorrect() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        assertEquals("S-CAR", quote.sessionId());
        assertEquals("KA01AB1234", quote.registration());
        assertEquals(VehicleType.CAR, quote.vehicleType());
        assertEquals("G-01", quote.slotId());
        assertEquals(Floor.GROUND, quote.floor());
        assertEquals(session.entryTime(), quote.entryTime());
        assertEquals(FOUR_HOURS_TWENTY_FOUR, quote.totalDuration());
        assertEquals(Duration.ofHours(2), quote.freeAllowance());
        assertEquals(Duration.ofHours(3), quote.chargeableDuration());
        assertMoney("5", quote.hourlyRate());
        assertMoney("15", quote.amountDue());
        assertTrue(quote.paymentRequired());
    }

    @Test
    void bikeQuoteIsCorrect() {
        fixture.seedActive(
                "S-BIKE", "KA02CD5678", VehicleType.BIKE, "G-02", FOUR_HOURS_TWENTY_FOUR);

        ExitQuote quote = quoteOf(fixture.service.quote("KA02CD5678"));

        assertEquals(VehicleType.BIKE, quote.vehicleType());
        assertEquals(FOUR_HOURS_TWENTY_FOUR, quote.totalDuration());
        assertEquals(Duration.ofHours(2), quote.freeAllowance());
        assertEquals(Duration.ofHours(3), quote.chargeableDuration());
        assertMoney("2", quote.hourlyRate());
        assertMoney("6", quote.amountDue());
    }

    @Test
    void exactlyTwoHoursIsFree() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", Duration.ofHours(2));

        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        assertEquals(Duration.ofHours(2), quote.totalDuration());
        assertMoney("0", quote.amountDue());
        assertEquals(Duration.ZERO, quote.chargeableDuration());
        assertFalse(quote.paymentRequired(), "A two-hour stay must not ask for payment");
    }

    @Test
    void oneSecondOverFreeAllowanceIsCharged() {
        Duration stay = Duration.ofHours(2).plusSeconds(1);
        fixture.seedActive("S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", stay);
        fixture.seedActive("S-BIKE", "KA02CD5678", VehicleType.BIKE, "G-02", stay);

        ExitQuote car = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitQuote bike = quoteOf(fixture.service.quote("KA02CD5678"));

        assertEquals(Duration.ofHours(1), car.chargeableDuration());
        assertEquals(Duration.ofHours(1), bike.chargeableDuration());
        assertMoney("5", car.amountDue());
        assertMoney("2", bike.amountDue());
    }

    @Test
    void partialChargeableHourRoundsUp() {
        fixture.seedActive("S-CAR-A", "KA01AB1234", VehicleType.CAR, "G-01",
                Duration.ofHours(2).plusMinutes(20));
        fixture.seedActive("S-CAR-B", "KA02CD5678", VehicleType.CAR, "G-02",
                Duration.ofHours(3).plusMinutes(10));
        fixture.seedActive("S-BIKE", "KA03EF9012", VehicleType.BIKE, "G-03",
                Duration.ofHours(3).plusMinutes(10));

        ExitQuote first = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitQuote second = quoteOf(fixture.service.quote("KA02CD5678"));
        ExitQuote bike = quoteOf(fixture.service.quote("KA03EF9012"));

        assertEquals(Duration.ofHours(1), first.chargeableDuration());
        assertMoney("5", first.amountDue());

        assertEquals(Duration.ofHours(2), second.chargeableDuration());
        assertMoney("10", second.amountDue());

        assertEquals(Duration.ofHours(2), bike.chargeableDuration());
        assertMoney("4", bike.amountDue());
    }

    @Test
    void longStayFee() {
        Duration stay = Duration.ofHours(24);
        fixture.seedActive("S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", stay);
        fixture.seedActive("S-BIKE", "KA02CD5678", VehicleType.BIKE, "G-02", stay);

        ExitQuote car = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitQuote bike = quoteOf(fixture.service.quote("KA02CD5678"));

        assertEquals(Duration.ofHours(22), car.chargeableDuration());
        assertEquals(Duration.ofHours(22), bike.chargeableDuration());
        assertMoney("110", car.amountDue());
        assertMoney("44", bike.amountDue());
    }

    @Test
    void quoteUsesOneCapturedExitTimestamp() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        assertEquals(NOW, quote.exitTime());
        assertEquals(session.entryTime(), quote.entryTime());

        BigDecimal recalculated = new FeeCalculator().calculateFee(
                quote.vehicleType(), quote.entryTime(), quote.exitTime());
        assertEquals(0, quote.amountDue().compareTo(recalculated),
                "The quoted amount must be the fee at the quote's own exit instant");
    }

    @Test
    void freshQuoteUsesFreshTimestamp() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        ExitQuote first = quoteOf(fixture.service.quote("KA01AB1234"));

        Clock tenMinutesLater = Clock.fixed(
                NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        ExitService laterService = new ExitService(
                fixture.slots, fixture.sessions, fixture.completions,
                new FeeCalculator(), tenMinutesLater);
        ExitQuote second = quoteOf(laterService.quote("KA01AB1234"));

        assertEquals(NOW, first.exitTime());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), second.exitTime());
        assertNotEquals(first.exitTime(), second.exitTime(),
                "A new service instance must capture a new exit instant");
        assertEquals(first.entryTime(), second.entryTime());
        assertEquals(0, fixture.paymentRowCount());
    }

    @Test
    void invalidRegistrationsRejected() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        long sessionsBefore = fixture.sessionCount();

        String[] invalidRegistrations = {null, "   ", "XYZ"};
        for (String registration : invalidRegistrations) {
            rejected(fixture.service.quote(registration),
                    ExitOutcome.Reason.INVALID_REGISTRATION);
        }

        assertEquals(sessionsBefore, fixture.sessionCount(),
                "Invalid plates must not alter any session row");
        assertEquals(0, fixture.paymentRowCount());
        assertTrue(fixture.sessions.findActiveByRegistration("KA01AB1234").isPresent());
    }

    @Test
    void unknownRegistrationIsNotFound() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        long sessionsBefore = fixture.sessionCount();

        rejected(fixture.service.quote("KA99ZZ9999"),
                ExitOutcome.Reason.SESSION_NOT_FOUND);

        assertEquals(sessionsBefore, fixture.sessionCount());
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
        assertTrue(fixture.sessions.findActiveByRegistration("KA01AB1234").isPresent());
    }

    @Test
    void completedSessionIsReported() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        fixture.sessions.updateCompleted(new ParkingSession(
                session.sessionId(),
                session.registration(),
                session.vehicleType(),
                false,
                session.slotId(),
                session.entryTime(),
                NOW,
                new BigDecimal("15")));

        rejected(fixture.service.quote("KA01AB1234"),
                ExitOutcome.Reason.ALREADY_COMPLETED);

        assertEquals(0, fixture.paymentRowCount(),
                "Direct completion must not invent a payment row");
        assertEquals(1, fixture.completedSessionCount());
    }

    @Test
    void quoteDoesNotModifySession() {
        ParkingSession original = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        ExitOutcome outcome = fixture.service.quote("KA01AB1234");
        assertInstanceOf(ExitOutcome.Quoted.class, outcome);

        ParkingSession stored = fixture.sessions.findById(original.sessionId()).orElseThrow();
        assertEquals(original, stored, "Quoting must not change the active session");
        assertNull(stored.exitTime());
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent(),
                "The bay must still be occupied after a quote");
    }

    // ------------------------------------------------------------------
    // Paid exit (scenarios 13-20)
    // ------------------------------------------------------------------

    @Test
    void confirmPaymentCompletesSessionAndWritesOnePayment() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        ExitSummary summary = completedOf(fixture.service.confirmPayment(quote));

        assertEquals(quote.sessionId(), summary.sessionId());
        assertEquals(quote.registration(), summary.registration());
        assertEquals(quote.vehicleType(), summary.vehicleType());
        assertEquals(quote.entryTime(), summary.entryTime());
        assertEquals(quote.exitTime(), summary.exitTime());
        assertEquals(quote.totalDuration(), summary.totalDuration());
        assertEquals(0, summary.amountPaid().compareTo(quote.amountDue()));
        assertTrue(summary.paid());
        assertNotNull(summary.paymentId());

        ParkingSession stored = fixture.sessions.findById(session.sessionId()).orElseThrow();
        assertEquals(quote.exitTime(), stored.exitTime(),
                "The confirmed exit must store the quoted instant");
        assertEquals(0, stored.finalFee().compareTo(quote.amountDue()),
                "The stored fee must be the quoted amount");
        assertNotNull(fixture.storedSessionText("exit_time", session.sessionId()));

        assertEquals(1, fixture.paymentRowCount());
        assertEquals(1, fixture.payments.countConfirmed());
        List<Payment> payments = fixture.payments.findBySessionId(quote.sessionId());
        assertEquals(1, payments.size());
        Payment payment = payments.get(0);
        assertEquals(PaymentStatus.CONFIRMED, payment.status());
        assertEquals(quote.sessionId(), payment.sessionId());
        assertEquals(0, payment.amount().compareTo(quote.amountDue()));
        assertEquals(summary.paymentId(), payment.paymentId());
        assertEquals(NOW, payment.confirmedAt());
        assertEquals(1, fixture.completedSessionCount());
    }

    @Test
    void paidExitFreesTheBay() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        completedOf(fixture.service.confirmPayment(quote));

        assertTrue(fixture.sessions.findActiveBySlot("G-01").isEmpty(),
                "A paid exit must release the bay");
        assertTrue(fixture.sessions.findActiveByRegistration("KA01AB1234").isEmpty());
        assertEquals(1, fixture.completedSessionCount());
    }

    @Test
    void paidExitPersistsAfterReopen() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitSummary summary = completedOf(fixture.service.confirmPayment(quote));

        Database reopened = new Database(fixture.database.file());
        ParkingSessionRepository sessionsAfterReopen =
                new JdbcParkingSessionRepository(reopened);
        PaymentRepository paymentsAfterReopen = new JdbcPaymentRepository(reopened);

        ParkingSession loaded = sessionsAfterReopen.findById(quote.sessionId()).orElseThrow();
        assertEquals(quote.exitTime(), loaded.exitTime());
        assertEquals(0, loaded.finalFee().compareTo(quote.amountDue()));
        assertEquals(1, sessionsAfterReopen.countCompletedExits());
        assertTrue(sessionsAfterReopen.findActive().isEmpty());

        List<Payment> storedPayments =
                paymentsAfterReopen.findBySessionId(quote.sessionId());
        assertEquals(1, storedPayments.size());
        assertEquals(PaymentStatus.CONFIRMED, storedPayments.get(0).status());
        assertEquals(0, storedPayments.get(0).amount().compareTo(quote.amountDue()));
        assertEquals(summary.paymentId(), storedPayments.get(0).paymentId());
        assertEquals(1, paymentsAfterReopen.countConfirmed());
    }

    @Test
    void secondConfirmationCannotChargeTwice() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitSummary first = completedOf(fixture.service.confirmPayment(quote));

        rejected(fixture.service.confirmPayment(quote),
                ExitOutcome.Reason.ALREADY_COMPLETED);

        assertEquals(1, fixture.paymentRowCount(),
                "A second confirmation must not insert another payment");
        assertEquals(1, fixture.payments.countConfirmed());
        assertEquals(1, fixture.completedSessionCount());

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertEquals(first.exitTime(), stored.exitTime(),
                "The first completion must remain the recorded exit");
        assertEquals(0, stored.finalFee().compareTo(first.amountPaid()));
    }

    @Test
    void staleQuoteCannotCloseAnotherSession() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        ExitQuote forged = new ExitQuote(
                quote.sessionId(),
                "KA99ZZ9999",
                quote.vehicleType(),
                "G-02",
                Floor.GROUND,
                quote.entryTime(),
                quote.exitTime(),
                quote.totalDuration(),
                quote.freeAllowance(),
                quote.chargeableDuration(),
                quote.hourlyRate(),
                quote.amountDue());

        rejected(fixture.service.confirmPayment(forged), ExitOutcome.Reason.STALE_QUOTE);

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime(), "A stale quote must not close the session");
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent());
    }

    @Test
    void tamperedFeeQuoteIsStale() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        ExitQuote tampered = new ExitQuote(
                quote.sessionId(),
                quote.registration(),
                quote.vehicleType(),
                quote.slotId(),
                quote.floor(),
                quote.entryTime(),
                quote.exitTime(),
                quote.totalDuration(),
                quote.freeAllowance(),
                quote.chargeableDuration(),
                quote.hourlyRate(),
                new BigDecimal("1"));

        rejected(fixture.service.confirmPayment(tampered), ExitOutcome.Reason.STALE_QUOTE);

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime());
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
    }

    @Test
    void paidQuoteCannotUseZeroFeePath() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        assertTrue(quote.paymentRequired());

        rejected(fixture.service.completeZeroFee(quote),
                ExitOutcome.Reason.PAYMENT_REQUIRED);

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime());
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent());
    }

    @Test
    void completedQuoteConfirmedAgain() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote paidQuote = quoteOf(fixture.service.quote("KA01AB1234"));
        ExitSummary confirmed = completedOf(fixture.service.confirmPayment(paidQuote));

        // A paid quote is refused before any database work by the zero-fee guard.
        rejected(fixture.service.completeZeroFee(paidQuote),
                ExitOutcome.Reason.PAYMENT_REQUIRED);

        // A zero-amount quote for the same, now closed session reaches the completion guard,
        // which must still refuse with ALREADY_COMPLETED and write nothing.
        ExitQuote zeroAmountQuote = new ExitQuote(
                paidQuote.sessionId(),
                paidQuote.registration(),
                paidQuote.vehicleType(),
                paidQuote.slotId(),
                paidQuote.floor(),
                session.entryTime(),
                session.entryTime().plus(Duration.ofHours(1)),
                Duration.ofHours(1),
                FeeCalculator.FREE_ALLOWANCE,
                Duration.ZERO,
                paidQuote.hourlyRate(),
                BigDecimal.ZERO);

        rejected(fixture.service.completeZeroFee(zeroAmountQuote),
                ExitOutcome.Reason.ALREADY_COMPLETED);

        ParkingSession stored = fixture.sessions.findById(paidQuote.sessionId()).orElseThrow();
        assertEquals(confirmed.exitTime(), stored.exitTime(),
                "The successful payment's exit must not be overwritten");
        assertEquals(0, stored.finalFee().compareTo(confirmed.amountPaid()));
        assertEquals(1, fixture.paymentRowCount());
        assertEquals(1, fixture.completedSessionCount());
    }

    // ------------------------------------------------------------------
    // Zero-fee exit (scenarios 21-22)
    // ------------------------------------------------------------------

    @Test
    void zeroFeeExitClosesWithoutPayment() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", Duration.ofHours(1));
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        assertMoney("0", quote.amountDue());
        assertFalse(quote.paymentRequired());

        ExitSummary summary = completedOf(fixture.service.completeZeroFee(quote));

        assertEquals(session.sessionId(), summary.sessionId());
        assertEquals(quote.exitTime(), summary.exitTime());
        assertEquals(quote.totalDuration(), summary.totalDuration());
        assertMoney("0", summary.amountPaid());
        assertNull(summary.paymentId(), "A zero-fee exit has no payment id");
        assertFalse(summary.paid());

        ParkingSession stored = fixture.sessions.findById(session.sessionId()).orElseThrow();
        assertEquals(quote.exitTime(), stored.exitTime());
        assertNotNull(stored.finalFee());
        assertMoney("0", stored.finalFee());
        assertEquals(0, fixture.paymentRowCount(),
                "A zero-fee exit must not write a payment row");
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isEmpty());
        assertEquals(1, fixture.completedSessionCount());
    }

    @Test
    void zeroFeeQuoteCannotConfirmPayment() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", Duration.ofHours(1));
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));

        rejected(fixture.service.confirmPayment(quote),
                ExitOutcome.Reason.NO_PAYMENT_REQUIRED);

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime(), "A refused payment must leave the session active");
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent());
    }

    // ------------------------------------------------------------------
    // Transaction rollback (scenarios 23-25)
    // ------------------------------------------------------------------

    @Test
    void paymentInsertFailureRollsBackCompletion() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        fixture.execute("""
                CREATE TRIGGER fail_payment_insert
                BEFORE INSERT ON payments
                BEGIN
                    SELECT RAISE(ABORT, 'payment failure');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> fixture.service.confirmPayment(quote),
                "A failed payment insert must propagate, never become a Completed result");

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime(),
                "The session update must roll back with the failed payment insert");
        assertNull(stored.finalFee());
        assertNull(fixture.storedSessionText("exit_time", quote.sessionId()));
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent(),
                "The bay must remain occupied when the transaction rolls back");
    }

    @Test
    void sessionUpdateFailureRollsBackEverything() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        fixture.execute("""
                CREATE TRIGGER fail_session_update
                BEFORE UPDATE ON parking_sessions
                BEGIN
                    SELECT RAISE(ABORT, 'session failure');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> fixture.service.confirmPayment(quote));

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime());
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount(),
                "No payment may survive a failed session update");
        assertEquals(0, fixture.completedSessionCount());
    }

    @Test
    void zeroFeeCompletionFailureLeavesSessionActive() {
        fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", Duration.ofHours(1));
        ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
        fixture.execute("""
                CREATE TRIGGER fail_session_update
                BEFORE UPDATE ON parking_sessions
                BEGIN
                    SELECT RAISE(ABORT, 'session failure');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> fixture.service.completeZeroFee(quote));

        ParkingSession stored = fixture.sessions.findById(quote.sessionId()).orElseThrow();
        assertNull(stored.exitTime());
        assertNull(stored.finalFee());
        assertEquals(0, fixture.paymentRowCount());
        assertEquals(0, fixture.completedSessionCount());
    }

    // ------------------------------------------------------------------
    // Quote is read-only (additional guarantees)
    // ------------------------------------------------------------------

    @Test
    void quoteNeverCreatesPayment() {
        ParkingSession session = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        BigDecimal calculated = new FeeCalculator().calculateFee(
                VehicleType.CAR, session.entryTime(), NOW);
        assertMoney("15", calculated);

        for (int attempt = 0; attempt < 3; attempt++) {
            quoteOf(fixture.service.quote("KA01AB1234"));
        }

        assertEquals(0, fixture.paymentRowCount(),
                "Calculating a fee must never create a payment row");
        assertEquals(0, fixture.payments.countConfirmed());
        assertEquals(0, fixture.completedSessionCount());
    }

    @Test
    void repeatedQuotesLeaveSessionUnchanged() {
        ParkingSession original = fixture.seedActive(
                "S-CAR", "KA01AB1234", VehicleType.CAR, "G-01", FOUR_HOURS_TWENTY_FOUR);

        for (int attempt = 0; attempt < 5; attempt++) {
            ExitQuote quote = quoteOf(fixture.service.quote("KA01AB1234"));
            assertEquals(NOW, quote.exitTime());
        }

        assertEquals(original,
                fixture.sessions.findById(original.sessionId()).orElseThrow(),
                "Repeated quotes must not change the session");
        assertEquals(1, fixture.sessionCount());
        assertEquals(0, fixture.completedSessionCount());
        assertEquals(0, fixture.paymentRowCount());
        assertTrue(fixture.sessions.findActiveBySlot("G-01").isPresent());
    }

    // ------------------------------------------------------------------
    // Assertion helpers
    // ------------------------------------------------------------------

    private static ExitQuote quoteOf(ExitOutcome outcome) {
        return assertInstanceOf(ExitOutcome.Quoted.class, outcome).quote();
    }

    private static ExitSummary completedOf(ExitOutcome outcome) {
        return assertInstanceOf(ExitOutcome.Completed.class, outcome).summary();
    }

    private static ExitOutcome.Rejected rejected(ExitOutcome outcome,
                                                 ExitOutcome.Reason reason) {
        ExitOutcome.Rejected rejected =
                assertInstanceOf(ExitOutcome.Rejected.class, outcome);
        assertEquals(reason, rejected.reason());
        return rejected;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                () -> "expected Rs " + expected + " but was Rs " + actual.toPlainString());
    }
}

package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.repository.ExitCompletionRepository;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.util.RegistrationUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The exit-kiosk use case: price an active stay, then close it exactly once.
 *
 * <p>A quote freezes its exit timestamp and its amount due. Confirmation reuses those exact
 * values instead of reading the clock again, so the customer is charged what was shown even if
 * they spend time on the payment panel.</p>
 *
 * <p>Closing a session (and, for a paid exit, inserting its confirmed demo payment) is delegated
 * to {@link ExitCompletionRepository}, which performs both writes in one transaction and reports
 * whether this attempt actually completed the session. Success is only reported after that
 * repository returns {@code true}.</p>
 *
 * <p>Domain refusals are returned as {@link ExitOutcome.Rejected}. A
 * {@link com.hilite.parking.repository.DataAccessException} is an unexpected persistence failure,
 * not a parking outcome, so it propagates untouched and can never turn into a false success.</p>
 */
public final class ExitService {

    private static final String MESSAGE_INVALID_REGISTRATION =
            "Enter a valid number plate, for example KL 11 AB 1234.";
    private static final String MESSAGE_SESSION_NOT_FOUND =
            "No active parking session was found for this number plate.";
    private static final String MESSAGE_ALREADY_COMPLETED =
            "This parking session has already been closed. No active session remains for this plate.";
    private static final String MESSAGE_STALE_QUOTE =
            "This fee quote is no longer valid. Calculate the fee again.";
    private static final String MESSAGE_PAYMENT_REQUIRED =
            "This session has an amount due. Confirm the demo payment to close it.";
    private static final String MESSAGE_NO_PAYMENT_REQUIRED =
            "No payment is due for this session.";

    private final ParkingSlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ExitCompletionRepository completionRepository;
    private final FeeCalculator feeCalculator;
    private final Clock clock;

    /**
     * Creates the service with the system UTC clock.
     *
     * @param slotRepository       read access to the fixed bay layout
     * @param sessionRepository    persistence for parking sessions
     * @param completionRepository atomic session-completion boundary
     * @param feeCalculator        the only fee arithmetic in the application
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExitService(ParkingSlotRepository slotRepository,
                       ParkingSessionRepository sessionRepository,
                       ExitCompletionRepository completionRepository,
                       FeeCalculator feeCalculator) {
        this(slotRepository, sessionRepository, completionRepository, feeCalculator,
                Clock.systemUTC());
    }

    /**
     * Creates the service with an explicit clock, so tests can pin the quote timestamp.
     *
     * @param slotRepository       read access to the fixed bay layout
     * @param sessionRepository    persistence for parking sessions
     * @param completionRepository atomic session-completion boundary
     * @param feeCalculator        the only fee arithmetic in the application
     * @param clock                source of quote and payment timestamps
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExitService(ParkingSlotRepository slotRepository,
                       ParkingSessionRepository sessionRepository,
                       ExitCompletionRepository completionRepository,
                       FeeCalculator feeCalculator,
                       Clock clock) {
        this.slotRepository = Objects.requireNonNull(
                slotRepository, "slotRepository is required");
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository is required");
        this.completionRepository = Objects.requireNonNull(
                completionRepository, "completionRepository is required");
        this.feeCalculator = Objects.requireNonNull(
                feeCalculator, "feeCalculator is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Prices the active session for a number plate.
     *
     * <p>The exit instant is read once, at the start of quote construction. Duration, hourly
     * rate, chargeable hours, and amount due are all derived from that same instant, so the
     * returned quote is internally consistent. This method performs no writes.</p>
     *
     * @param registration number plate as typed; normalized before lookup
     * @return a frozen quote, or a reason the plate cannot be quoted
     * @throws com.hilite.parking.repository.DataAccessException if a repository read fails
     */
    public ExitOutcome quote(String registration) {
        Optional<String> validated = validateRegistration(registration);
        if (validated.isEmpty()) {
            return new ExitOutcome.Rejected(
                    ExitOutcome.Reason.INVALID_REGISTRATION, MESSAGE_INVALID_REGISTRATION);
        }
        String normalized = validated.get();

        Optional<ParkingSession> active =
                sessionRepository.findActiveByRegistration(normalized);
        if (active.isPresent()) {
            return new ExitOutcome.Quoted(buildQuote(active.get()));
        }

        // Distinguish a plate that has never parked from one that has already exited.
        if (sessionRepository.findLatestByRegistration(normalized).isPresent()) {
            return alreadyCompleted();
        }
        return sessionNotFound();
    }

    /**
     * Closes a zero-fee stay without recording a payment.
     *
     * @param quote quote from {@link #quote(String)}; must not be {@code null}
     * @return the saved completion, or a refusal when payment is still due
     * @throws NullPointerException                               if {@code quote} is {@code null}
     * @throws com.hilite.parking.repository.DataAccessException if completion fails
     */
    public ExitOutcome completeZeroFee(ExitQuote quote) {
        Objects.requireNonNull(quote, "quote is required");
        if (quote.paymentRequired()) {
            return new ExitOutcome.Rejected(
                    ExitOutcome.Reason.PAYMENT_REQUIRED, MESSAGE_PAYMENT_REQUIRED);
        }
        return complete(quote, null);
    }

    /**
     * Records a confirmed demo payment and closes the stay in one transaction.
     *
     * @param quote quote from {@link #quote(String)}; must not be {@code null}
     * @return the saved completion, or a refusal when no payment is due
     * @throws NullPointerException                               if {@code quote} is {@code null}
     * @throws com.hilite.parking.repository.DataAccessException if completion fails
     */
    public ExitOutcome confirmPayment(ExitQuote quote) {
        Objects.requireNonNull(quote, "quote is required");
        if (!quote.paymentRequired()) {
            return new ExitOutcome.Rejected(
                    ExitOutcome.Reason.NO_PAYMENT_REQUIRED, MESSAGE_NO_PAYMENT_REQUIRED);
        }

        Payment payment = new Payment(
                UUID.randomUUID().toString(),
                quote.sessionId(),
                quote.amountDue(),
                PaymentStatus.CONFIRMED,
                Instant.now(clock));
        return complete(quote, payment);
    }

    /**
     * Shared completion path for both exit kinds.
     *
     * <p>Re-reading the session catches attempts made with an old quote. The quote is accepted
     * only if it still describes the stored active session and its frozen amount matches a fee
     * recalculated at the quoted exit instant. The actual close is then delegated to one atomic,
     * conditional repository call; only its {@code true} result becomes a success.</p>
     */
    private ExitOutcome complete(ExitQuote quote, Payment payment) {
        Optional<ParkingSession> stored = sessionRepository.findById(quote.sessionId());
        if (stored.isEmpty()) {
            return sessionNotFound();
        }
        ParkingSession session = stored.get();

        if (session.exitTime() != null || session.finalFee() != null) {
            return alreadyCompleted();
        }

        if (!quoteMatches(quote, session)) {
            return staleQuote();
        }

        boolean completed = completionRepository.complete(
                quote.sessionId(), quote.exitTime(), quote.amountDue(), payment);
        if (!completed) {
            // Another kiosk completed the session between our read and this write.
            return alreadyCompleted();
        }

        ExitSummary summary = new ExitSummary(
                quote.sessionId(),
                quote.registration(),
                quote.vehicleType(),
                session.entryTime(),
                quote.exitTime(),
                Duration.between(session.entryTime(), quote.exitTime()),
                quote.amountDue(),
                payment == null ? null : payment.paymentId());
        return new ExitOutcome.Completed(summary);
    }

    /** Builds the quote from one active session and one freshly captured exit instant. */
    private ExitQuote buildQuote(ParkingSession session) {
        Instant exitTime = Instant.now(clock);
        BigDecimal amountDue = feeCalculator.calculateFee(
                session.vehicleType(), session.entryTime(), exitTime);
        BigDecimal hourlyRate = feeCalculator.hourlyRate(session.vehicleType());

        // Derive the billed hours from the calculator's fee instead of repeating its rounding.
        long billedHours = amountDue.signum() == 0
                ? 0
                : amountDue.divide(hourlyRate).longValueExact();
        Duration chargeableDuration = Duration.ofHours(billedHours);
        Duration totalDuration = Duration.between(session.entryTime(), exitTime);

        ParkingSlot slot = slotRepository.findById(session.slotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Slot " + session.slotId() + " is missing for session "
                                + session.sessionId()));

        return new ExitQuote(
                session.sessionId(),
                session.registration(),
                session.vehicleType(),
                slot.slotId(),
                slot.floor(),
                session.entryTime(),
                exitTime,
                totalDuration,
                FeeCalculator.FREE_ALLOWANCE,
                chargeableDuration,
                hourlyRate,
                amountDue);
    }

    /**
     * Checks that a quote still describes the stored session and that its frozen fee is the fee
     * the calculator produces for the quote's own exit instant.
     */
    private boolean quoteMatches(ExitQuote quote, ParkingSession session) {
        if (!quote.sessionId().equals(session.sessionId())
                || !quote.registration().equals(session.registration())
                || quote.vehicleType() != session.vehicleType()
                || !quote.slotId().equals(session.slotId())
                || !quote.entryTime().equals(session.entryTime())) {
            return false;
        }
        BigDecimal recalculated = feeCalculator.calculateFee(
                session.vehicleType(), session.entryTime(), quote.exitTime());
        return recalculated.compareTo(quote.amountDue()) == 0;
    }

    /**
     * Normalizes and validates the plate, returning empty when it cannot be used.
     * A null or empty value makes {@link RegistrationUtil#normalize(String)} throw, which is
     * treated as a normal validation failure rather than a crash.
     */
    private static Optional<String> validateRegistration(String registration) {
        try {
            String normalized = RegistrationUtil.normalize(registration);
            return RegistrationUtil.isValid(normalized)
                    ? Optional.of(normalized)
                    : Optional.empty();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    private static ExitOutcome sessionNotFound() {
        return new ExitOutcome.Rejected(
                ExitOutcome.Reason.SESSION_NOT_FOUND, MESSAGE_SESSION_NOT_FOUND);
    }

    private static ExitOutcome alreadyCompleted() {
        return new ExitOutcome.Rejected(
                ExitOutcome.Reason.ALREADY_COMPLETED, MESSAGE_ALREADY_COMPLETED);
    }

    private static ExitOutcome staleQuote() {
        return new ExitOutcome.Rejected(ExitOutcome.Reason.STALE_QUOTE, MESSAGE_STALE_QUOTE);
    }
}

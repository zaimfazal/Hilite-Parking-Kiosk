package com.hilite.parking.service;

import java.util.Objects;

/**
 * The result of one exit-kiosk operation.
 *
 * <p>The three cases are deliberately exclusive: a fee {@link Quoted quote}, a saved
 * {@link Completed completion}, or a {@link Rejected refusal}. A caller that switches on this
 * type cannot show a success screen before a session has actually been closed.</p>
 *
 * <p>Domain refusals (unknown plate, already closed session, stale quote, wrong completion
 * path) arrive as {@link Rejected}. Unexpected persistence failures are not parking outcomes:
 * {@link com.hilite.parking.repository.DataAccessException} propagates so the controller can
 * show a generic error, and a failed transaction never produces {@link Completed}.</p>
 */
public sealed interface ExitOutcome {

    /** Why an exit request could not be quoted or completed. */
    enum Reason {

        /** The plate is empty, or does not match the demo plate format after normalization. */
        INVALID_REGISTRATION,

        /** No session at all exists for the plate. */
        SESSION_NOT_FOUND,

        /** The session for the plate has already been closed. */
        ALREADY_COMPLETED,

        /** The quote no longer matches the stored active session; it must be recalculated. */
        STALE_QUOTE,

        /** A zero-fee completion was attempted for a quote that still has an amount due. */
        PAYMENT_REQUIRED,

        /** A demo payment was confirmed for a quote that has no amount due. */
        NO_PAYMENT_REQUIRED
    }

    /** A fee quote for an active session. The session is still open at this point. */
    record Quoted(ExitQuote quote) implements ExitOutcome {

        public Quoted {
            Objects.requireNonNull(quote, "quote is required");
        }
    }

    /** A session that has been closed and saved. */
    record Completed(ExitSummary summary) implements ExitOutcome {

        public Completed {
            Objects.requireNonNull(summary, "summary is required");
        }
    }

    /** A refused request: no session was altered and no payment was recorded. */
    record Rejected(Reason reason, String message) implements ExitOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason is required");
            Objects.requireNonNull(message, "message is required");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }
}

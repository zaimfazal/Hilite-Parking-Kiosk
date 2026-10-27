package com.hilite.parking.repository.jdbc;

import com.hilite.parking.repository.DataAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Conversions between the Java model and the text/integer columns of SQLite.
 *
 * <p>Timestamps are stored as ISO-8601 UTC text with exactly nine fractional digits
 * (30 characters), for example {@code 2026-10-25T09:15:30.000000000Z}. Money is stored as
 * integer paise, never as floating point. Keeping both rules in one place means every
 * repository writes the same representation.</p>
 */
final class SqlCodec {

    /** Fixed UTC timestamp layout used in every text timestamp column. */
    static final DateTimeFormatter INSTANT_TEXT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private SqlCodec() {
    }

    /** Formats an instant as the fixed UTC text stored in the database. */
    static String toText(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        return INSTANT_TEXT.format(instant);
    }

    /** Parses the ISO-8601 UTC text stored in the database. */
    static Instant parseInstant(String text) {
        Objects.requireNonNull(text, "text is required");
        return Instant.parse(text);
    }

    /**
     * Converts a rupee amount to whole paise.
     *
     * @throws IllegalArgumentException if the amount is not a whole number of paise
     */
    static long toPaise(BigDecimal rupees) {
        Objects.requireNonNull(rupees, "amount is required");
        try {
            return rupees.movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "amount must be a whole number of paise: " + rupees.toPlainString(), e);
        }
    }

    /** Converts whole paise back to a rupee amount with scale 2. */
    static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    /** Wraps a SQLite failure in the repository exception, keeping the original cause. */
    static DataAccessException failure(String action, SQLException cause) {
        return new DataAccessException(action + " failed: " + cause.getMessage(), cause);
    }
}

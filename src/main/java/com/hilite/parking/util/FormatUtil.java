package com.hilite.parking.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small shared formatting helpers so dates, money and plates read consistently. */
public final class FormatUtil {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_ONLY =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private FormatUtil() {
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? "—" : DATE_TIME.format(dateTime);
    }

    /**
     * Formats a stored UTC {@link Instant} in the machine's local zone for display.
     * Persisted timestamps stay UTC; only the presentation layer converts them.
     */
    public static String dateTime(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return dateTime(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    public static String shortDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "—" : DATE_SHORT.format(dateTime);
    }

    public static String time(LocalDateTime dateTime) {
        return dateTime == null ? "—" : TIME_ONLY.format(dateTime);
    }

    /** Example output: "4 h 24 min", "3 h" or "45 min". */
    public static String duration(Duration duration) {
        if (duration == null) {
            return "—";
        }
        long totalMinutes = Math.max(0, duration.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return hours + " h " + minutes + " min";
        }
        if (hours > 0) {
            return hours + " h";
        }
        return minutes + " min";
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "₹0";
        }
        if (amount.stripTrailingZeros().scale() <= 0) {
            return "₹" + amount.setScale(0).toPlainString();
        }
        return "₹" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Display-only normalization: trim, collapse repeated spaces and uppercase.
     * Persisted registrations use {@link RegistrationUtil#normalize(String)} instead, which
     * also removes spaces and hyphens.
     */
    public static String normalizePlate(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
